package com.dxc.monitoring.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dxc.monitoring.entity.Machine;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.UserRepository;

@Service
public class StreamingJobService
{
    private static final int OUTPUT_BUFFER_LINES = 500;
    private static final long STOP_GRACE_SECONDS = 5;

    private final MonitoringJobRepository jobRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<Long, RunContext> activeRuns = new ConcurrentHashMap<>();
    private final Map<Long, Deque<String>> outputBuffers = new ConcurrentHashMap<>();
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Value("${monitoring.streaming.max-runtime-seconds:86400}")
    private int configuredMaximumRuntimeSeconds;
    @Value("${monitoring.streaming.ssh-user:}")
    private String sshUser;
    @Value("${monitoring.streaming.ssh-key-path:}")
    private String sshKeyPath;
    @Value("${monitoring.streaming.ssh-port:22}")
    private int sshPort;

    public StreamingJobService(MonitoringJobRepository jobRepository, UserRepository userRepository,
            JdbcTemplate jdbcTemplate)
    {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StreamingJobView> listJobs()
    {
        List<MonitoringJob> jobs = jobRepository.findByExecutionModeAndEnabledTrueOrderByNameAsc(
                MonitoringJob.ExecutionMode.STREAMING);
        List<StreamingJobView> views = new ArrayList<>();
        for (MonitoringJob job : jobs)
        {
            ClaimRow claim = readClaim(job.getId());
            views.add(new StreamingJobView(job.getId(), job.getName(), job.getDescription(),
                    claim == null ? "IDLE" : claim.status,
                    claim == null ? null : claim.startedBy,
                    claim == null ? null : claim.startedAt,
                    claim == null ? null : claim.processId,
                    job.getMaxStreamingRuntimeSeconds() == null ? 300 : job.getMaxStreamingRuntimeSeconds(),
                    claim == null ? null : claim.terminalMessage));
        }
        return views;
    }

    public void start(Long jobId, String username)
    {
        MonitoringJob job = jobRepository.findByIdForDetails(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + jobId));
        if (!job.isEnabled() || job.getExecutionMode() != MonitoringJob.ExecutionMode.STREAMING)
            throw new IllegalStateException("Only enabled streaming jobs can be started here.");
        if (!job.isManualRunEnabled())
            throw new IllegalStateException("Manual execution is disabled for this job.");
        if (job.getScriptPath() == null || job.getScriptPath().isBlank())
            throw new IllegalStateException("A script path is required for a streaming job.");

        int runtime = job.getMaxStreamingRuntimeSeconds() == null ? 300 : job.getMaxStreamingRuntimeSeconds();
        if (runtime < 1 || runtime > configuredMaximumRuntimeSeconds)
            throw new IllegalStateException("Streaming runtime must be between 1 and "
                    + configuredMaximumRuntimeSeconds + " seconds.");

        Long userId = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Current user was not found.")).getId();

        // This single PostgreSQL statement is the cross-instance atomic claim. Active or
        // recovery-required claims cannot be replaced, regardless of heartbeat age.
        int claimed = jdbcTemplate.update("""
                INSERT INTO ra_fcb.streaming_job_claims
                    (monitoring_job_id, status, started_by, started_at, updated_at, heartbeat_at, terminal_message)
                VALUES (?, 'STARTING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                ON CONFLICT (monitoring_job_id) DO UPDATE SET
                    status='STARTING', started_by=EXCLUDED.started_by,
                    started_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP,
                    heartbeat_at=CURRENT_TIMESTAMP, process_id=NULL, process_host=NULL,
                    process_marker=NULL, remote_log_path=NULL, terminal_message=NULL
                WHERE ra_fcb.streaming_job_claims.status IN ('IDLE','COMPLETED','STOPPED','FAILED','TIMED_OUT')
                """, jobId, userId);
        if (claimed != 1)
            throw new IllegalStateException("This job is already running or requires recovery.");

        RunContext context = new RunContext(jobId, runtime);
        if (activeRuns.putIfAbsent(jobId, context) != null)
        {
            jdbcTemplate.update("""
                    UPDATE ra_fcb.streaming_job_claims SET status='RECOVERY_REQUIRED',
                        updated_at=CURRENT_TIMESTAMP,
                        terminal_message='An existing local process context must be recovered.'
                    WHERE monitoring_job_id=?
                    """, jobId);
            throw new IllegalStateException("A previous run context exists; recovery is required.");
        }

        appendOutput(jobId, "Starting streaming job: " + job.getName());
        context.timeoutTask = scheduler.schedule(() -> stopInternal(jobId, "TIMED_OUT",
                "Maximum runtime of " + runtime + " seconds reached."), runtime, TimeUnit.SECONDS);
        context.heartbeatTask = scheduler.scheduleAtFixedRate(() -> heartbeat(jobId), 10, 10, TimeUnit.SECONDS);
        scheduler.execute(() -> launch(job, context));
    }

    public void stop(Long jobId)
    {
        ClaimRow claim = readClaim(jobId);
        if (claim == null || !isActiveStatus(claim.status))
            throw new IllegalStateException("The streaming job is not running.");

        jdbcTemplate.update("""
                UPDATE ra_fcb.streaming_job_claims SET status='STOPPING', updated_at=CURRENT_TIMESTAMP
                WHERE monitoring_job_id=? AND status IN ('STARTING','RUNNING','STOPPING','RECOVERY_REQUIRED')
                """, jobId);
        publishState(jobId, "STOPPING");

        RunContext context = activeRuns.get(jobId);
        if (context != null)
        {
            stopInternal(jobId, "STOPPED", "Stopped by an authorized user.");
            return;
        }

        // After a restart, only release the claim when the persisted process identity can be
        // verified and termination is confirmed. Unverifiable PIDs remain blocked.
        if (terminatePersistedProcess(claim))
        {
            finishClaim(jobId, "STOPPED", "Stopped after recovery.");
            appendOutput(jobId, "Streaming process stopped during recovery.");
        }
        else
        {
            jdbcTemplate.update("""
                    UPDATE ra_fcb.streaming_job_claims SET status='RECOVERY_REQUIRED',
                        updated_at=CURRENT_TIMESTAMP,
                        terminal_message='Process identity or termination could not be confirmed; the job remains blocked.'
                    WHERE monitoring_job_id=?
                    """, jobId);
            publishState(jobId, "RECOVERY_REQUIRED");
            throw new IllegalStateException("Could not safely verify or terminate the prior process. The job remains blocked.");
        }
    }

    public SseEmitter subscribe(Long jobId)
    {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> listeners = emitters.computeIfAbsent(jobId, ignored -> new CopyOnWriteArraySet<>());
        listeners.add(emitter);
        emitter.onCompletion(() -> listeners.remove(emitter));
        emitter.onTimeout(() -> listeners.remove(emitter));
        emitter.onError(error -> listeners.remove(emitter));

        List<String> buffered;
        Deque<String> buffer = buffer(jobId);
        synchronized (buffer) { buffered = new ArrayList<>(buffer); }
        try
        {
            ClaimRow claim = readClaim(jobId);
            emitter.send(SseEmitter.event().name("state").data(Map.of("status",
                    claim == null ? "IDLE" : claim.status)));
            for (String line : buffered)
                emitter.send(SseEmitter.event().name("line").data(line));
        }
        catch (IOException exception)
        {
            listeners.remove(emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedRunsForRecovery()
    {
        // Never release a claim just because its heartbeat is stale. The stop/recovery action
        // verifies the recorded PID and leaves uncertain processes blocked.
        jdbcTemplate.update("""
                UPDATE ra_fcb.streaming_job_claims
                SET status='RECOVERY_REQUIRED', updated_at=CURRENT_TIMESTAMP,
                    terminal_message=COALESCE(terminal_message,
                        'Application restarted; verify and terminate the prior process before another run.')
                WHERE status IN ('STARTING','RUNNING','STOPPING')
                """);
    }

    private void launch(MonitoringJob job, RunContext context)
    {
        try
        {
            Machine machine = job.getMachine();
            String host = machine == null ? null :
                    (machine.getHostname() != null && !machine.getHostname().isBlank()
                            ? machine.getHostname().trim() : machine.getIpAddress());
            boolean remote = host != null && !host.isBlank()
                    && !host.equalsIgnoreCase("localhost") && !host.equals("127.0.0.1");
            if (remote) launchRemote(job, host, context);
            else launchLocal(job, context);
        }
        catch (Exception exception)
        {
            appendOutput(job.getId(), "Unable to start streaming process: " + safeMessage(exception));
            stopInternal(job.getId(), "FAILED", safeMessage(exception));
        }
    }

    private void launchLocal(MonitoringJob job, RunContext context) throws IOException
    {
        ProcessBuilder builder = new ProcessBuilder("bash", "-lc", buildScriptCommand(job)).redirectErrorStream(true);
        if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
            builder.directory(new java.io.File(job.getWorkingDirectory()));
        Process process = builder.start();
        context.process = process;
        context.remote = false;
        context.pid = process.pid();
        context.host = "LOCAL";
        context.marker = job.getScriptPath();
        persistProcess(job.getId(), context);
        updateClaimStatus(job.getId(), "RUNNING");
        appendOutput(job.getId(), "Process started locally (PID " + context.pid + ").");
        scheduler.execute(() -> readLines(job.getId(), process));
        scheduler.execute(() -> waitForExit(job.getId(), context, process));
    }

    private void launchRemote(MonitoringJob job, String host, RunContext context) throws Exception
    {
        if (sshUser == null || sshUser.isBlank())
            throw new IllegalStateException("Set MONITORING_STREAMING_SSH_USER to enable remote streaming jobs.");
        String logPath = "/tmp/monitoring-stream-" + job.getId() + "-" + System.currentTimeMillis() + ".log";
        String remoteCommand = "mkdir -p /tmp; nohup bash -lc " + shellQuote(buildScriptCommand(job))
                + " > " + shellQuote(logPath) + " 2>&1 < /dev/null & echo $!";
        Process launcher = new ProcessBuilder(sshCommand(host, remoteCommand)).redirectErrorStream(true).start();
        if (!launcher.waitFor(15, TimeUnit.SECONDS))
        {
            launcher.destroyForcibly();
            throw new IllegalStateException("Timed out while starting the remote process.");
        }
        String pidText = new String(launcher.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (launcher.exitValue() != 0 || !pidText.matches("\\d+"))
            throw new IllegalStateException("Remote launch failed: " + pidText);

        context.remote = true;
        context.pid = Long.parseLong(pidText);
        context.host = host;
        context.marker = job.getScriptPath();
        context.remoteLogPath = logPath;
        persistProcess(job.getId(), context);
        updateClaimStatus(job.getId(), "RUNNING");
        appendOutput(job.getId(), "Remote process started on " + host + " (PID " + context.pid + ").");

        Process tail = new ProcessBuilder(sshCommand(host, "tail -n 0 -F " + shellQuote(logPath)))
                .redirectErrorStream(true).start();
        context.outputProcess = tail;
        scheduler.execute(() -> readLines(job.getId(), tail));
        scheduler.execute(() -> waitForExit(job.getId(), context, tail));
    }

    private void waitForExit(Long jobId, RunContext context, Process observedProcess)
    {
        try
        {
            int exit = observedProcess.waitFor();
            if (context.stopping.get()) return;
            if (context.remote)
            {
                // Loss of the output SSH connection is not proof the remote process ended.
                jdbcTemplate.update("""
                        UPDATE ra_fcb.streaming_job_claims SET status='RECOVERY_REQUIRED',
                            updated_at=CURRENT_TIMESTAMP,
                            terminal_message='Remote output connection ended; remote process state must be verified.'
                        WHERE monitoring_job_id=? AND status='RUNNING'
                        """, jobId);
                publishState(jobId, "RECOVERY_REQUIRED");
                appendOutput(jobId, "Remote output connection ended (exit " + exit
                        + "); process state requires verification.");
            }
            else stopInternal(jobId, exit == 0 ? "COMPLETED" : "FAILED",
                    exit == 0 ? "Process completed." : "Process exited with code " + exit + ".");
        }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private void readLines(Long jobId, Process process)
    {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null) appendOutput(jobId, line);
        }
        catch (IOException exception) { appendOutput(jobId, "Output reader ended: " + safeMessage(exception)); }
    }

    private void stopInternal(Long jobId, String terminalStatus, String message)
    {
        RunContext context = activeRuns.get(jobId);
        if (context == null || !context.stopping.compareAndSet(false, true)) return;
        updateClaimStatus(jobId, "STOPPING");
        if (!terminateContext(context))
        {
            jdbcTemplate.update("""
                    UPDATE ra_fcb.streaming_job_claims SET status='RECOVERY_REQUIRED',
                        updated_at=CURRENT_TIMESTAMP,
                        terminal_message='Termination could not be confirmed; new runs remain blocked.'
                    WHERE monitoring_job_id=?
                    """, jobId);
            publishState(jobId, "RECOVERY_REQUIRED");
            appendOutput(jobId, "Termination could not be confirmed; job remains blocked for recovery.");
            return;
        }
        finishClaim(jobId, terminalStatus, message);
        appendOutput(jobId, message);
        activeRuns.remove(jobId, context);
        if (context.timeoutTask != null) context.timeoutTask.cancel(false);
        if (context.heartbeatTask != null) context.heartbeatTask.cancel(false);
    }

    private boolean terminateContext(RunContext context)
    {
        try
        {
            if (context.remote)
            {
                boolean terminated = terminateRemote(context.host, context.pid, context.marker);
                if (context.outputProcess != null && context.outputProcess.isAlive())
                {
                    context.outputProcess.destroy();
                    if (!context.outputProcess.waitFor(STOP_GRACE_SECONDS, TimeUnit.SECONDS))
                        context.outputProcess.destroyForcibly();
                }
                return terminated;
            }
            Process process = context.process;
            if (process == null) return false;
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            if (!process.waitFor(STOP_GRACE_SECONDS, TimeUnit.SECONDS))
            {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(STOP_GRACE_SECONDS, TimeUnit.SECONDS);
            }
            return !process.isAlive() && (context.outputProcess == null || !context.outputProcess.isAlive());
        }
        catch (Exception exception) { return false; }
    }

    private boolean terminatePersistedProcess(ClaimRow claim)
    {
        if (claim.processId == null) return false;
        String host = claim.processHost == null ? "LOCAL" : claim.processHost;
        String marker = claim.processMarker == null ? "" : claim.processMarker;
        if (host.equals("LOCAL"))
        {
            ProcessHandle handle = ProcessHandle.of(claim.processId).orElse(null);
            if (handle == null || !handle.isAlive()) return true;
            String command = handle.info().commandLine().orElse("");
            if (marker.isBlank() || !command.contains(marker)) return false;
            handle.descendants().forEach(ProcessHandle::destroy);
            handle.destroy();
            try
            {
                Thread.sleep(STOP_GRACE_SECONDS * 1000);
                if (handle.isAlive())
                {
                    handle.descendants().forEach(ProcessHandle::destroyForcibly);
                    handle.destroyForcibly();
                }
                return !handle.isAlive();
            }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
        }
        return terminateRemote(host, claim.processId, marker);
    }

    private boolean terminateRemote(String host, Long pid, String marker)
    {
        if (pid == null || host == null || host.equals("LOCAL")) return true;
        if (marker == null || marker.isBlank()) return false;
        String command = "args=$(ps -p " + pid + " -o args= 2>/dev/null) || exit 0; "
                + "printf '%s' \"$args\" | grep -F -- " + shellQuote(marker) + " >/dev/null || exit 42; "
                + "pkill -TERM -P " + pid + " 2>/dev/null || true; kill -TERM " + pid + " 2>/dev/null || true; "
                + "for i in 1 2 3 4 5; do kill -0 " + pid + " 2>/dev/null || exit 0; sleep 1; done; "
                + "pkill -KILL -P " + pid + " 2>/dev/null || true; kill -KILL " + pid + " 2>/dev/null || true; "
                + "kill -0 " + pid + " 2>/dev/null && exit 43 || exit 0";
        try
        {
            Process process = new ProcessBuilder(sshCommand(host, command)).redirectErrorStream(true).start();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        }
        catch (Exception exception) { return false; }
    }

    private List<String> sshCommand(String host, String remoteCommand)
    {
        List<String> command = new ArrayList<>(List.of("ssh", "-p", Integer.toString(sshPort),
                "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes"));
        if (sshKeyPath != null && !sshKeyPath.isBlank()) { command.add("-i"); command.add(sshKeyPath); }
        command.add(sshUser + "@" + host);
        command.add(remoteCommand);
        return command;
    }

    private String buildScriptCommand(MonitoringJob job)
    {
        List<String> parts = new ArrayList<>();
        parts.add(shellQuote(job.getScriptPath()));
        if (job.getCommandArguments() != null && !job.getCommandArguments().isBlank())
            for (String argument : job.getCommandArguments().trim().split("\\s+"))
                if (!argument.isBlank()) parts.add(shellQuote(argument));
        String command = String.join(" ", parts);
        return job.getWorkingDirectory() == null || job.getWorkingDirectory().isBlank()
                ? "exec " + command
                : "cd " + shellQuote(job.getWorkingDirectory()) + " && exec " + command;
    }

    private String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }

    private void persistProcess(Long jobId, RunContext context)
    {
        jdbcTemplate.update("""
                UPDATE ra_fcb.streaming_job_claims
                SET process_id=?, process_host=?, process_marker=?, remote_log_path=?,
                    updated_at=CURRENT_TIMESTAMP, heartbeat_at=CURRENT_TIMESTAMP
                WHERE monitoring_job_id=? AND status IN ('STARTING','RUNNING')
                """, context.pid, context.host, context.marker, context.remoteLogPath, jobId);
    }

    private void updateClaimStatus(Long jobId, String status)
    {
        jdbcTemplate.update("""
                UPDATE ra_fcb.streaming_job_claims SET status=?, updated_at=CURRENT_TIMESTAMP,
                    heartbeat_at=CURRENT_TIMESTAMP WHERE monitoring_job_id=?
                """, status, jobId);
        publishState(jobId, status);
    }

    private void finishClaim(Long jobId, String status, String message)
    {
        jdbcTemplate.update("""
                UPDATE ra_fcb.streaming_job_claims SET status=?, updated_at=CURRENT_TIMESTAMP,
                    heartbeat_at=CURRENT_TIMESTAMP, terminal_message=? WHERE monitoring_job_id=?
                """, status, message, jobId);
        publishState(jobId, status);
    }

    private void heartbeat(Long jobId)
    {
        jdbcTemplate.update("""
                UPDATE ra_fcb.streaming_job_claims SET heartbeat_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP WHERE monitoring_job_id=? AND status='RUNNING'
                """, jobId);
    }

    private void appendOutput(Long jobId, String line)
    {
        Deque<String> buffer = buffer(jobId);
        String formatted = OffsetDateTime.now() + " " + (line == null ? "" : line);
        synchronized (buffer)
        {
            while (buffer.size() >= OUTPUT_BUFFER_LINES) buffer.removeFirst();
            buffer.addLast(formatted);
        }
        for (SseEmitter emitter : emitters.getOrDefault(jobId, Set.of()))
        {
            try { emitter.send(SseEmitter.event().name("line").data(formatted)); }
            catch (IOException exception) { emitters.getOrDefault(jobId, Set.of()).remove(emitter); }
        }
    }

    private void publishState(Long jobId, String status)
    {
        for (SseEmitter emitter : emitters.getOrDefault(jobId, Set.of()))
        {
            try { emitter.send(SseEmitter.event().name("state").data(Map.of("status", status))); }
            catch (IOException exception) { emitters.getOrDefault(jobId, Set.of()).remove(emitter); }
        }
    }

    private Deque<String> buffer(Long jobId) { return outputBuffers.computeIfAbsent(jobId, ignored -> new ArrayDeque<>()); }

    private ClaimRow readClaim(Long jobId)
    {
        List<ClaimRow> rows = jdbcTemplate.query("""
                SELECT status, started_by, started_at, process_id, process_host,
                       process_marker, remote_log_path, terminal_message
                FROM ra_fcb.streaming_job_claims WHERE monitoring_job_id=?
                """, (rs, row) -> new ClaimRow(rs.getString("status"), rs.getObject("started_by", Long.class),
                    rs.getObject("started_at", OffsetDateTime.class), rs.getObject("process_id", Long.class),
                    rs.getString("process_host"), rs.getString("process_marker"),
                    rs.getString("remote_log_path"), rs.getString("terminal_message")), jobId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isActiveStatus(String status)
    {
        return Set.of("STARTING", "RUNNING", "STOPPING", "RECOVERY_REQUIRED").contains(status);
    }

    private String safeMessage(Exception exception)
    {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @PreDestroy
    public void shutdown()
    {
        for (Long jobId : new ArrayList<>(activeRuns.keySet()))
            stopInternal(jobId, "STOPPED", "Application is shutting down.");
        scheduler.shutdownNow();
    }

    public record StreamingJobView(Long id, String name, String description, String status, Long startedBy,
            OffsetDateTime startedAt, Long processId, Integer maxRuntimeSeconds, String message) {}
    private record ClaimRow(String status, Long startedBy, OffsetDateTime startedAt, Long processId,
            String processHost, String processMarker, String remoteLogPath, String terminalMessage) {}
    private static final class RunContext
    {
        private final Long jobId;
        private final int runtimeSeconds;
        private final AtomicBoolean stopping = new AtomicBoolean();
        private volatile Process process;
        private volatile Process outputProcess;
        private volatile boolean remote;
        private volatile Long pid;
        private volatile String host;
        private volatile String marker;
        private volatile String remoteLogPath;
        private volatile ScheduledFuture<?> timeoutTask;
        private volatile ScheduledFuture<?> heartbeatTask;
        private RunContext(Long jobId, int runtimeSeconds) { this.jobId = jobId; this.runtimeSeconds = runtimeSeconds; }
    }
}
