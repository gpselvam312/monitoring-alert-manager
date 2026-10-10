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
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

@Service
public class StreamingJobService
{
    private static final int OUTPUT_BUFFER_LINES = 500;
    private static final long STOP_GRACE_SECONDS = 5;

    private final MonitoringJobRepository jobRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DashboardAccessService accessService;
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
    @Value("${monitoring.streaming.node-id:${HOSTNAME:localhost}}")
    private String nodeId;

    public StreamingJobService(MonitoringJobRepository jobRepository, UserRepository userRepository,
            JdbcTemplate jdbcTemplate, DashboardAccessService accessService)
    {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<StreamingJobView> listJobs()
    {
        List<Long> applicationIds = accessService.getAccessibleApplications().stream()
                .map(com.dxc.monitoring.entity.Application::getId).toList();
        if (applicationIds.isEmpty()) return List.of();
        List<MonitoringJob> jobs = jobRepository.findByExecutionModeAndApplication_IdInOrderByNameAsc(
                MonitoringJob.ExecutionMode.STREAMING, applicationIds);
        List<StreamingJobView> views = new ArrayList<>();
        for (MonitoringJob job : jobs)
        {
            ClaimRow claim = readClaim(job.getId());
            views.add(new StreamingJobView(job.getId(), job.getName(), job.getDescription(),
                    job.getApplication() == null ? null : job.getApplication().getName(),
                    job.getEnvironment() == null ? null : job.getEnvironment().getName(),
                    job.getMachine() == null ? null : job.getMachine().getName(),
                    job.getType() == null ? null : job.getType().name(),
                    job.getSeverity() == null ? null : job.getSeverity().name(),
                    job.getSchedule() == null ? null : job.getSchedule().getName(),
                    job.isEnabled(), job.isManualRunEnabled(),
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
        if (job.getApplication() == null)
            throw new org.springframework.security.access.AccessDeniedException("Streaming job is not assigned to an application.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_RUN");
        if (!job.isEnabled() || job.getExecutionMode() != MonitoringJob.ExecutionMode.STREAMING)
            throw new IllegalStateException("Only enabled streaming jobs can be started here.");
        if (!job.isManualRunEnabled())
            throw new IllegalStateException("Manual execution is disabled for this job.");
        if (job.getType() != MonitoringJob.MonitorType.SCRIPT)
            throw new IllegalStateException("Streaming execution currently supports SCRIPT monitoring jobs only.");
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
                INSERT INTO ra_fcb.streaming_job_claims AS existing
                    (monitoring_job_id, status, started_by, claim_owner, started_at, updated_at, heartbeat_at, terminal_message)
                VALUES (?, 'STARTING', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                ON CONFLICT (monitoring_job_id) DO UPDATE SET
                    status='STARTING', started_by=EXCLUDED.started_by, claim_owner=EXCLUDED.claim_owner,
                    started_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP,
                    heartbeat_at=CURRENT_TIMESTAMP, process_id=NULL, process_host=NULL,
                    process_marker=NULL, remote_log_path=NULL, terminal_message=NULL
                WHERE existing.status IN ('IDLE','COMPLETED','STOPPED','FAILED','TIMED_OUT')
                """, jobId, userId, nodeId);
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
        scheduler.execute(() -> launch(job, context));
    }

    public void stop(Long jobId)
    {
        MonitoringJob job = jobRepository.findByIdForDetails(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + jobId));
        if (job.getApplication() == null)
            throw new org.springframework.security.access.AccessDeniedException("Streaming job is not assigned to an application.");
        accessService.assertCanAccessApplication(job.getApplication().getId(), "MONITORING_RUN");
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
        MonitoringJob job = jobRepository.findByIdForDetails(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + jobId));
        if (!job.isEnabled() || job.getExecutionMode() != MonitoringJob.ExecutionMode.STREAMING)
            throw new IllegalArgumentException("Only enabled streaming jobs expose a live output stream.");

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
        List<Long> interrupted = jdbcTemplate.queryForList("""
                SELECT monitoring_job_id FROM ra_fcb.streaming_job_claims
                WHERE claim_owner=? AND status IN ('STARTING','RUNNING','STOPPING')
                """, Long.class, nodeId);
        jdbcTemplate.update("""
                UPDATE ra_fcb.streaming_job_claims
                SET status='RECOVERY_REQUIRED', updated_at=CURRENT_TIMESTAMP,
                    terminal_message=COALESCE(terminal_message,
                        'Application restarted; verifying the prior process before allowing another run.')
                WHERE claim_owner=? AND status IN ('STARTING','RUNNING','STOPPING')
                """, nodeId);
        for (Long jobId : interrupted)
        {
            ClaimRow claim = readClaim(jobId);
            if (claim != null && terminatePersistedProcess(claim))
                finishClaim(jobId, "STOPPED", "Prior process was confirmed stopped during application recovery.");
            else
                appendOutput(jobId, "Recovery could not verify process termination; this job remains blocked.");
        }
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
            if (!context.remote && context.process == null && context.pid == null)
            {
                context.stopping.set(true);
                finishClaim(job.getId(), "FAILED", safeMessage(exception));
                activeRuns.remove(job.getId(), context);
            }
            else
            {
                stopInternal(job.getId(), "FAILED", safeMessage(exception));
            }
        }
    }

    private void launchLocal(MonitoringJob job, RunContext context) throws IOException
    {
        ProcessBuilder builder = new ProcessBuilder("setsid", "bash", "-lc", buildScriptCommand(job)).redirectErrorStream(true);
        if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
            builder.directory(new java.io.File(job.getWorkingDirectory()));
        Process process = builder.start();
        context.process = process;
        context.remote = false;
        context.pid = process.pid();
        context.host = nodeId;
        context.marker = job.getScriptPath();
        persistProcess(job.getId(), context);
        updateClaimStatus(job.getId(), "RUNNING");
        activateRuntime(job.getId(), context);
        appendOutput(job.getId(), "Process started locally (PID " + context.pid + ").");
        scheduler.execute(() -> readLines(job.getId(), process));
        scheduler.execute(() -> waitForExit(job.getId(), context, process));
    }

    private void launchRemote(MonitoringJob job, String host, RunContext context) throws Exception
    {
        if (sshUser == null || sshUser.isBlank())
            throw new IllegalStateException("Set MONITORING_STREAMING_SSH_USER to enable remote streaming jobs.");
        String logPath = "/tmp/monitoring-stream-" + job.getId() + "-" + System.currentTimeMillis() + ".log";
        String exitPath = logPath + ".exit";
        String remoteScript = buildScriptCommand(job) + "; rc=$?; printf '%s' \"$rc\" > "
                + shellQuote(exitPath) + "; exit \"$rc\"";
        context.remote = true;
        context.host = host;
        context.marker = job.getScriptPath();
        context.remoteLogPath = logPath;
        persistProcess(job.getId(), context);
        String remoteCommand = "mkdir -p /tmp; rm -f " + shellQuote(exitPath) + "; nohup setsid bash -lc "
                + shellQuote(remoteScript) + " > " + shellQuote(logPath) + " 2>&1 < /dev/null & echo $!";
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
        activateRuntime(job.getId(), context);
        appendOutput(job.getId(), "Remote process started on " + host + " (PID " + context.pid + ").");

        String tailCommand = "tail -n +1 -F " + shellQuote(logPath) + " & tail_pid=$!; "
                + "while kill -0 " + context.pid + " 2>/dev/null; do sleep 1; done; sleep 0.5; "
                + "kill \"$tail_pid\" 2>/dev/null || true; wait \"$tail_pid\" 2>/dev/null || true";
        Process tail = new ProcessBuilder(sshCommand(host, tailCommand)).redirectErrorStream(true).start();
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
                Integer remoteExit = readRemoteExitCode(context);
                if (remoteExit == null)
                {
                    jdbcTemplate.update("""
                            UPDATE ra_fcb.streaming_job_claims SET status='RECOVERY_REQUIRED',
                                updated_at=CURRENT_TIMESTAMP,
                                terminal_message='Remote process exit could not be confirmed.'
                            WHERE monitoring_job_id=? AND status='RUNNING'
                            """, jobId);
                    publishState(jobId, "RECOVERY_REQUIRED");
                    appendOutput(jobId, "Remote output connection ended without a confirmed process exit; recovery is required.");
                }
                else
                {
                    // The remote process has already exited. Do not try to kill its PID;
                    // finalize directly so a normal exit is not mistaken for failed recovery.
                    if (context.stopping.compareAndSet(false, true))
                    {
                        String terminalStatus = remoteExit == 0 ? "COMPLETED" : "FAILED";
                        String terminalMessage = remoteExit == 0 ? "Remote process completed."
                                : "Remote process exited with code " + remoteExit + ".";
                        finishClaim(jobId, terminalStatus, terminalMessage);
                        appendOutput(jobId, terminalMessage);
                        activeRuns.remove(jobId, context);
                        if (context.timeoutTask != null) context.timeoutTask.cancel(false);
                        if (context.heartbeatTask != null) context.heartbeatTask.cancel(false);
                    }
                }
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
            // Allow an authorized user or a later timeout/recovery attempt to retry termination.
            context.stopping.set(false);
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
            if (process == null || context.pid == null) return false;
            boolean terminated = terminateLocalProcessGroup(context.pid, context.marker, false);
            if (context.outputProcess != null && context.outputProcess.isAlive())
            {
                context.outputProcess.destroy();
                if (!context.outputProcess.waitFor(STOP_GRACE_SECONDS, TimeUnit.SECONDS))
                    context.outputProcess.destroyForcibly();
            }
            return terminated && !process.isAlive()
                    && (context.outputProcess == null || !context.outputProcess.isAlive());
        }
        catch (Exception exception) { return false; }
    }

    private boolean terminatePersistedProcess(ClaimRow claim)
    {
        if (claim.processId == null) return false;
        String host = claim.processHost == null ? "LOCAL" : claim.processHost;
        String marker = claim.processMarker == null ? "" : claim.processMarker;
        if (host.equals(nodeId))
        {
            return terminateLocalProcessGroup(claim.processId, marker, true);
        }
        return terminateRemote(host, claim.processId, marker);
    }

    private boolean terminateLocalProcessGroup(Long pid, String marker, boolean requireMarker)
    {
        if (pid == null) return false;
        String groupArguments = "ps -eo pgid=,args= | awk '$1 == " + pid
                + " { $1 = \"\"; sub(/^ /, \"\"); print }'";
        String command = "group_args=$(" + groupArguments + "); "
                + "if [ -z \"$group_args\" ]; then exit 0; fi; "
                + (requireMarker
                        ? "printf '%s' \"$group_args\" | grep -F -- " + shellQuote(marker == null ? "" : marker)
                                + " >/dev/null || exit 42; "
                        : "")
                + "kill -TERM -- -" + pid + " 2>/dev/null || true; "
                + "for i in 1 2 3 4 5; do "
                + "ps -eo pgid= | awk '$1 == " + pid + " { found=1 } END { exit !found }' || exit 0; sleep 1; done; "
                + "kill -KILL -- -" + pid + " 2>/dev/null || true; "
                + "ps -eo pgid= | awk '$1 == " + pid + " { found=1 } END { exit !found }' && exit 43 || exit 0";
        try
        {
            Process process = new ProcessBuilder("bash", "-lc", command).redirectErrorStream(true).start();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        }
        catch (Exception exception) { return false; }
    }

    private boolean terminateRemote(String host, Long pid, String marker)
    {
        if (pid == null || host == null || host.equals("LOCAL")) return false;
        if (marker == null || marker.isBlank()) return false;
        String groupArguments = "ps -eo pgid=,args= | awk '$1 == " + pid
                + " { $1 = \"\"; sub(/^ /, \"\"); print }'";
        String command = "group_args=$(" + groupArguments + "); "
                + "if [ -z \"$group_args\" ]; then exit 0; fi; "
                + "printf '%s' \"$group_args\" | grep -F -- " + shellQuote(marker) + " >/dev/null || exit 42; "
                + "kill -TERM -- -" + pid + " 2>/dev/null || true; "
                + "for i in 1 2 3 4 5; do "
                + "ps -eo pgid= | awk '$1 == " + pid + " { found=1 } END { exit !found }' || exit 0; sleep 1; done; "
                + "kill -KILL -- -" + pid + " 2>/dev/null || true; "
                + "ps -eo pgid= | awk '$1 == " + pid + " { found=1 } END { exit !found }' && exit 43 || exit 0";
        try
        {
            Process process = new ProcessBuilder(sshCommand(host, command)).redirectErrorStream(true).start();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        }
        catch (Exception exception) { return false; }
    }

    private Integer readRemoteExitCode(RunContext context)
    {
        try
        {
            Process process = new ProcessBuilder(sshCommand(context.host,
                    "cat " + shellQuote(context.remoteLogPath + ".exit"))).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS))
            {
                process.destroyForcibly();
                return null;
            }
            String code = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && code.matches("-?\\d+") ? Integer.valueOf(code) : null;
        }
        catch (Exception exception)
        {
            return null;
        }
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
                ? command
                : "cd " + shellQuote(job.getWorkingDirectory()) + " && " + command;
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

    private void activateRuntime(Long jobId, RunContext context)
    {
        context.timeoutTask = scheduler.schedule(() -> stopInternal(jobId, "TIMED_OUT",
                "Maximum runtime of " + context.runtimeSeconds + " seconds reached."),
                context.runtimeSeconds, TimeUnit.SECONDS);
        context.heartbeatTask = scheduler.scheduleAtFixedRate(() -> heartbeat(jobId), 10, 10, TimeUnit.SECONDS);
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

    public record StreamingJobView(Long id, String name, String description, String application, String environment,
            String machine, String type, String severity, String schedule, boolean enabled, boolean manualRunEnabled,
            String status, Long startedBy, OffsetDateTime startedAt, Long processId, Integer maxRuntimeSeconds,
            String message) {}
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
