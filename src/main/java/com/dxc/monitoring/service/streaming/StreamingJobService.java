package com.dxc.monitoring.service.streaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dxc.monitoring.entity.Machine;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.StreamingJobClaim;
import com.dxc.monitoring.entity.StreamingJobClaim.ClaimStatus;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.StreamingJobClaimRepository;

@Service
public class StreamingJobService
{
    private static final int DEFAULT_RUNTIME_SECONDS = 300;
    private static final int MAX_RUNTIME_SECONDS = 86400;

    private final MonitoringJobRepository jobRepository;
    private final StreamingJobClaimRepository claimRepository;
    private final String instanceId = UUID.randomUUID().toString();
    private final ExecutorService workers = Executors.newCachedThreadPool(r ->
    {
        Thread thread = new Thread(r, "streaming-job-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService eventWorkers = Executors.newCachedThreadPool(r ->
    {
        Thread thread = new Thread(r, "streaming-job-events");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, Process> localProcesses = new ConcurrentHashMap<>();

    @Value("${monitoring.streaming.ssh.username:}")
    private String sshUsername;

    @Value("${monitoring.streaming.ssh.private-key-path:}")
    private String sshPrivateKeyPath;

    public StreamingJobService(MonitoringJobRepository jobRepository,
            StreamingJobClaimRepository claimRepository)
    {
        this.jobRepository = jobRepository;
        this.claimRepository = claimRepository;
    }

    public List<StreamingJobView> listEnabledJobs()
    {
        List<MonitoringJob> jobs = jobRepository.findByExecutionModeAndEnabledTrueOrderByNameAsc(
                MonitoringJob.ExecutionMode.STREAMING);
        for (MonitoringJob job : jobs)
        {
            claimRepository.ensureRow(job.getId());
        }
        return jobs.stream().map(job -> getJob(job.getId())).toList();
    }

    public StreamingJobView getJob(Long jobId)
    {
        StreamingJobClaim claim = claimRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Streaming job claim not found for job " + jobId));
        MonitoringJob job = claim.getMonitoringJob();
        long remaining = 0;
        if (claim.getDeadlineAt() != null && claim.getStatus().isActive())
        {
            remaining = Math.max(0, Duration.between(OffsetDateTime.now(), claim.getDeadlineAt()).getSeconds());
        }
        String machineName = job.getMachine() == null ? "Local machine" : job.getMachine().getName();
        return new StreamingJobView(job.getId(), job.getName(), job.getDescription(), machineName,
                claim.getStatus().name(), claim.getStartedAt(), claim.getDeadlineAt(),
                claim.getOutputBuffer(), claim.getErrorMessage(), remaining);
    }

    public StreamingJobView start(Long jobId, String username)
    {
        MonitoringJob job = jobRepository.findByIdForDetails(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring job not found: " + jobId));

        if (!job.isEnabled() || job.getExecutionMode() != MonitoringJob.ExecutionMode.STREAMING)
        {
            throw new IllegalStateException("Only enabled streaming jobs can be started here.");
        }
        if (!job.isManualRunEnabled())
        {
            throw new IllegalStateException("Manual execution is disabled for this job.");
        }
        if (job.getType() != MonitoringJob.MonitorType.SCRIPT)
        {
            throw new IllegalStateException("Streaming execution currently requires a SCRIPT monitoring job.");
        }
        if (job.getScriptPath() == null || job.getScriptPath().isBlank()
                || job.getScriptPath().contains("\n") || job.getScriptPath().contains("\r"))
        {
            throw new IllegalArgumentException("A valid script path is required.");
        }

        int maxRuntime = job.getMaxStreamingRuntimeSeconds() == null
                ? DEFAULT_RUNTIME_SECONDS
                : Math.max(1, Math.min(MAX_RUNTIME_SECONDS, job.getMaxStreamingRuntimeSeconds()));

        claimRepository.ensureRow(jobId);
        int claimed = claimRepository.claim(jobId, instanceId, safeUsername(username), maxRuntime);
        if (claimed != 1)
        {
            StreamingJobClaim current = claimRepository.findByJobId(jobId).orElse(null);
            String state = current == null ? "unknown" : current.getStatus().name();
            throw new IllegalStateException("This streaming job cannot start while its current state is " + state + ".");
        }

        try
        {
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(job)).redirectErrorStream(true);
            if (!isRemote(job) && job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
            {
                processBuilder.directory(new java.io.File(job.getWorkingDirectory()));
            }
            Process process = processBuilder.start();
            localProcesses.put(jobId, process);
            OffsetDateTime processStartedAt = process.toHandle().info().startInstant()
                    .map(instant -> OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)).orElse(null);
            claimRepository.markRunning(jobId, instanceId, process.pid(), processStartedAt, null);
            workers.submit(() -> monitorProcess(job, process));
            return getJob(jobId);
        }
        catch (Exception exception)
        {
            claimRepository.finish(jobId, instanceId, "FAILED", null, safeMessage(exception));
            throw new IllegalStateException("Unable to start streaming job: " + safeMessage(exception), exception);
        }
    }

    public StreamingJobView stop(Long jobId)
    {
        StreamingJobClaim claim = claimRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Streaming job claim not found for job " + jobId));

        if (!claim.getStatus().isActive())
        {
            return getJob(jobId);
        }

        claimRepository.requestStop(jobId);
        Process process = localProcesses.get(jobId);
        if (process != null && !isRemote(claim.getMonitoringJob()))
        {
            terminateLocalProcess(process);
        }
        else if (process == null && claim.getOwnerInstanceId() != null
                && !instanceId.equals(claim.getOwnerInstanceId()))
        {
            recoverClaim(claim);
        }
        return getJob(jobId);
    }

    public SseEmitter stream(Long jobId)
    {
        SseEmitter emitter = new SseEmitter(0L);
        eventWorkers.submit(() ->
        {
            try
            {
                while (true)
                {
                    StreamingJobView view = getJob(jobId);
                    emitter.send(SseEmitter.event().name("snapshot").data(view));
                    ClaimStatus status = ClaimStatus.valueOf(view.status());
                    if (!status.isActive())
                    {
                        break;
                    }
                    Thread.sleep(1000);
                }
                emitter.complete();
            }
            catch (Exception exception)
            {
                emitter.completeWithError(exception);
            }
        });
        emitter.onCompletion(() -> { });
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverActiveClaimsOnStartup()
    {
        for (StreamingJobClaim claim : claimRepository.findEnabledStreamingClaims(MonitoringJob.ExecutionMode.STREAMING))
        {
            if (claim.getStatus().isActive())
            {
                recoverClaim(claim);
            }
        }
    }

    private void monitorProcess(MonitoringJob job, Process process)
    {
        Long jobId = job.getId();
        Future<?> outputReader = workers.submit(() -> readOutput(job, process));
        boolean timedOut = false;
        boolean stopRequested = false;
        boolean safeToFinish = true;

        try
        {
            while (process.isAlive())
            {
                StreamingJobClaim claim = claimRepository.findByJobId(jobId).orElse(null);
                if (claim == null)
                {
                    safeToFinish = false;
                    break;
                }

                if (claim.getStatus() == ClaimStatus.STOPPING)
                {
                    stopRequested = true;
                    safeToFinish = terminateProcess(job, process, claim);
                    break;
                }

                if (claim.getDeadlineAt() != null && !OffsetDateTime.now().isBefore(claim.getDeadlineAt()))
                {
                    timedOut = true;
                    safeToFinish = terminateProcess(job, process, claim);
                    break;
                }

                process.waitFor(1, TimeUnit.SECONDS);
            }

            if (!safeToFinish)
            {
                claimRepository.markRecoveryRequired(jobId, "Process termination could not be confirmed; new runs are blocked.");
                return;
            }

            if (process.isAlive())
            {
                process.waitFor(5, TimeUnit.SECONDS);
            }
            if (process.isAlive())
            {
                claimRepository.markRecoveryRequired(jobId, "Process remains alive after termination request.");
                return;
            }

            try
            {
                outputReader.get(2, TimeUnit.SECONDS);
            }
            catch (Exception ignored)
            {
                outputReader.cancel(true);
            }

            int exitCode = process.exitValue();
            String status = timedOut ? "TIMED_OUT" : stopRequested ? "STOPPED" : exitCode == 0 ? "COMPLETED" : "FAILED";
            String message = timedOut ? "Maximum streaming runtime reached."
                    : stopRequested ? "Stopped by an authorized user."
                    : exitCode == 0 ? null : "Streaming process exited with code " + exitCode;
            claimRepository.finish(jobId, instanceId, status, exitCode, message);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            boolean terminated = terminateProcess(job, process,
                    claimRepository.findByJobId(jobId).orElse(null));
            if (terminated && !process.isAlive())
            {
                claimRepository.finish(jobId, instanceId, "STOPPED", process.exitValue(),
                        "Streaming worker was interrupted.");
            }
            else
            {
                claimRepository.markRecoveryRequired(jobId, "Worker interrupted and process termination is uncertain.");
            }
        }
        catch (Exception exception)
        {
            claimRepository.markRecoveryRequired(jobId, "Streaming process state is uncertain: " + safeMessage(exception));
        }
        finally
        {
            localProcesses.remove(jobId, process);
        }
    }

    private void readOutput(MonitoringJob job, Process process)
    {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (isRemote(job) && line.startsWith("__STREAM_PID="))
                {
                    String pid = line.substring("__STREAM_PID=".length()).trim();
                    if (pid.matches("[0-9]+"))
                    {
                        claimRepository.setRemotePid(job.getId(), instanceId, pid);
                    }
                    continue;
                }
                claimRepository.appendOutput(job.getId(), instanceId, line + System.lineSeparator());
            }
        }
        catch (IOException exception)
        {
            claimRepository.appendOutput(job.getId(), instanceId,
                    System.lineSeparator() + "[output stream closed: " + safeMessage(exception) + "]" + System.lineSeparator());
        }
    }

    private boolean terminateProcess(MonitoringJob job, Process process, StreamingJobClaim claim)
    {
        if (isRemote(job))
        {
            String remotePid = claim == null ? null : claim.getRemotePid();
            if (remotePid == null)
            {
                // Allow the SSH wrapper a short time to publish its remote PID.
                for (int i = 0; i < 3 && remotePid == null; i++)
                {
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    remotePid = claimRepository.findByJobId(job.getId()).map(StreamingJobClaim::getRemotePid).orElse(null);
                }
            }
            if (remotePid == null || !remotePid.matches("[0-9]+"))
            {
                process.destroy();
                return false;
            }
            if (!terminateRemote(job, remotePid))
            {
                process.destroy();
                return false;
            }
        }

        terminateLocalProcess(process);
        return !process.isAlive();
    }

    private void terminateLocalProcess(Process process)
    {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroy);
        handle.destroy();
        try
        {
            if (!process.waitFor(3, TimeUnit.SECONDS))
            {
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                handle.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            handle.descendants().forEach(ProcessHandle::destroyForcibly);
            handle.destroyForcibly();
        }
    }

    private void recoverClaim(StreamingJobClaim claim)
    {
        MonitoringJob job = claim.getMonitoringJob();
        Long jobId = job.getId();

        try
        {
            if (isRemote(job))
            {
                String remotePid = claim.getRemotePid();
                if (remotePid != null && remotePid.matches("[0-9]+") && terminateRemote(job, remotePid))
                {
                    claimRepository.finishRecovery(jobId, "STOPPED",
                            "Recovered after application restart; remote process termination confirmed.");
                }
                else
                {
                    claimRepository.markRecoveryRequired(jobId,
                            "Previous remote process could not be verified or terminated. Manual recovery is required.");
                }
                return;
            }

            Long pid = claim.getProcessId();
            if (pid == null)
            {
                claimRepository.markRecoveryRequired(jobId,
                        "Previous process identifier is missing; automatic recovery cannot safely release the claim.");
                return;
            }

            ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
            if (handle == null || !handle.isAlive())
            {
                claimRepository.finishRecovery(jobId, "STOPPED",
                        "Recovered after restart; the previous local process is no longer running.");
                return;
            }

            Instant actualStart = handle.info().startInstant().orElse(null);
            Instant recordedStart = claim.getProcessStartTime() == null ? null
                    : claim.getProcessStartTime().toInstant();
            if (actualStart == null || recordedStart == null
                    || Math.abs(Duration.between(recordedStart, actualStart).toSeconds()) > 2)
            {
                claimRepository.markRecoveryRequired(jobId,
                        "Process identity could not be verified; claim remains blocked for manual recovery.");
                return;
            }

            handle.descendants().forEach(ProcessHandle::destroy);
            handle.destroy();
            if (!handle.onExit().get(3, TimeUnit.SECONDS).isAlive())
            {
                claimRepository.finishRecovery(jobId, "STOPPED",
                        "Recovered after restart; previous local process was terminated.");
            }
            else
            {
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                handle.destroyForcibly();
                if (!handle.onExit().get(3, TimeUnit.SECONDS).isAlive())
                {
                    claimRepository.finishRecovery(jobId, "STOPPED",
                            "Recovered after restart; previous local process was force-terminated.");
                }
                else
                {
                    claimRepository.markRecoveryRequired(jobId,
                            "Previous local process could not be terminated; new runs remain blocked.");
                }
            }
        }
        catch (Exception exception)
        {
            claimRepository.markRecoveryRequired(jobId,
                    "Automatic recovery failed: " + safeMessage(exception));
        }
    }

    private boolean terminateRemote(MonitoringJob job, String pid)
    {
        if (pid == null || !pid.matches("[0-9]+"))
        {
            return false;
        }
        try
        {
            List<String> command = sshBaseCommand(job);
            String remoteCommand = "pid=" + pid
                    + "; kill -TERM \"$pid\" 2>/dev/null || true; "
                    + "for i in 1 2 3 4 5; do kill -0 \"$pid\" 2>/dev/null || exit 0; sleep 1; done; "
                    + "kill -KILL \"$pid\" 2>/dev/null || true; sleep 1; "
                    + "kill -0 \"$pid\" 2>/dev/null && exit 1 || exit 0";
            command.add(remoteCommand);
            Process killer = new ProcessBuilder(command).redirectErrorStream(true).start();
            return killer.waitFor(15, TimeUnit.SECONDS) && killer.exitValue() == 0;
        }
        catch (Exception exception)
        {
            return false;
        }
    }

    private List<String> buildCommand(MonitoringJob job)
    {
        if (!isRemote(job))
        {
            List<String> command = new ArrayList<>();
            command.add(job.getScriptPath());
            command.addAll(parseArguments(job.getCommandArguments()));
            ProcessBuilder builder = new ProcessBuilder(command);
            if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
            {
                builder.directory(new java.io.File(job.getWorkingDirectory()));
            }
            return command;
        }

        String remoteScript = "echo __STREAM_PID=$$; ";
        if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
        {
            remoteScript += "cd " + shellQuote(job.getWorkingDirectory()) + " && ";
        }
        remoteScript += "exec " + shellQuote(job.getScriptPath());
        for (String argument : parseArguments(job.getCommandArguments()))
        {
            remoteScript += " " + shellQuote(argument);
        }

        List<String> command = sshBaseCommand(job);
        command.add("bash -lc " + shellQuote(remoteScript));
        return command;
    }

    private List<String> sshBaseCommand(MonitoringJob job)
    {
        Machine machine = job.getMachine();
        if (machine == null)
        {
            throw new IllegalArgumentException("A target machine is required for SSH streaming execution.");
        }
        String host = machine.getHostname() == null || machine.getHostname().isBlank()
                ? machine.getIpAddress() : machine.getHostname();
        if (host == null || host.isBlank())
        {
            throw new IllegalArgumentException("The target machine has no hostname or IP address.");
        }
        String user = sshUsername == null ? "" : sshUsername.trim();
        if (host.contains("@") && user.isEmpty())
        {
            user = host.substring(0, host.indexOf('@'));
            host = host.substring(host.indexOf('@') + 1);
        }
        if (user.isBlank())
        {
            throw new IllegalStateException("Configure monitoring.streaming.ssh.username for remote streaming jobs.");
        }

        List<String> command = new ArrayList<>(List.of("ssh", "-o", "BatchMode=yes",
                "-o", "ConnectTimeout=10", "-o", "StrictHostKeyChecking=yes"));
        if (sshPrivateKeyPath != null && !sshPrivateKeyPath.isBlank())
        {
            command.add("-i");
            command.add(sshPrivateKeyPath);
        }
        command.add(user + "@" + host);
        return command;
    }

    private boolean isRemote(MonitoringJob job)
    {
        if (job.getMachine() == null)
        {
            return false;
        }
        Machine machine = job.getMachine();
        String host = machine.getHostname();
        if (host == null || host.isBlank())
        {
            host = machine.getIpAddress();
        }
        if (host == null || host.isBlank())
        {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("@"))
        {
            normalized = normalized.substring(normalized.indexOf('@') + 1);
        }
        return !normalized.equals("localhost") && !normalized.equals("127.0.0.1") && !normalized.equals("::1");
    }

    private List<String> parseArguments(String arguments)
    {
        if (arguments == null || arguments.isBlank())
        {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (String token : arguments.trim().split("\\s+"))
        {
            if (!token.isBlank())
            {
                parsed.add(token);
            }
        }
        return parsed;
    }

    private String shellQuote(String value)
    {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String safeUsername(String username)
    {
        if (username == null || username.isBlank())
        {
            return "unknown";
        }
        return username.length() > 100 ? username.substring(0, 100) : username;
    }

    private String safeMessage(Exception exception)
    {
        String message = exception.getMessage();
        if (message == null || message.isBlank())
        {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
