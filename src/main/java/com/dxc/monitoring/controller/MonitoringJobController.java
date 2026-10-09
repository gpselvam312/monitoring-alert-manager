package com.dxc.monitoring.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.MachineRepository;
import com.dxc.monitoring.repository.ScheduleRepository;
import com.dxc.monitoring.repository.UserRepository;
import com.dxc.monitoring.service.MonitoringExecutionManager;
import com.dxc.monitoring.service.MonitoringExecutionService;
import com.dxc.monitoring.service.MonitoringJobService;

@Controller
@RequestMapping("/monitoring/jobs")
public class MonitoringJobController
{

    private final MonitoringJobService monitoringJobService;
    private final MachineRepository machineRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final EnvironmentRepository environmentRepository;
    private final MonitoringExecutionManager monitoringExecutionManager;
    private final MonitoringExecutionService monitoringExecutionService;

    @Value("${monitoring.streaming.max-runtime-seconds:86400}")
    private int configuredMaximumStreamingRuntimeSeconds;

    private static final Map<String, String> JOB_SORT_FIELDS =
        Map.of("name", "name", "application.name", "application.name", "environment.name", "environment.name",
                "machine.name", "machine.name", "type", "type", "severity", "severity", "schedule.name",
                "schedule.name", "enabled", "enabled");

    public MonitoringJobController(MonitoringJobService monitoringJobService, MachineRepository machineRepository,
            ScheduleRepository scheduleRepository, UserRepository userRepository,
            ApplicationRepository applicationRepository, EnvironmentRepository environmentRepository,
            MonitoringExecutionManager monitoringExecutionManager,
            MonitoringExecutionService monitoringExecutionService)
    {
        this.monitoringJobService = monitoringJobService;
        this.machineRepository = machineRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.environmentRepository = environmentRepository;
        this.monitoringExecutionManager = monitoringExecutionManager;
        this.monitoringExecutionService = monitoringExecutionService;
    }

    /*
     * VIEW MONITORING JOBS
     */
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    @GetMapping
    public String listJobs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        if (page < 0)
        {
            page = 0;
        }

        if (size != 5 && size != 10 && size != 25)
        {
            size = 5;
        }

        String normalizedSearch = search == null ? "" : search.trim();

        Sort pageableSort = buildJobSort(sort, direction);

        Pageable pageable = PageRequest.of(page, size, pageableSort);

        Page<MonitoringJob> jobs = monitoringJobService.findAll(normalizedSearch, pageable);

        model.addAttribute("jobs", jobs);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("currentPage", "monitoring-jobs");

        return "monitoring/jobs";
    }

    private Sort buildJobSort(String sort, String direction)
    {
        String sortField = JOB_SORT_FIELDS.getOrDefault(sort, "name");

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return Sort.by(sortDirection, sortField);
    }

    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    @GetMapping("/table")
    public String jobTable(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        if (page < 0)
        {
            page = 0;
        }

        if (size != 5 && size != 10 && size != 25)
        {
            size = 5;
        }

        String normalizedSearch = search == null ? "" : search.trim();

        Sort pageableSort = buildJobSort(sort, direction);

        Pageable pageable = PageRequest.of(page, size, pageableSort);

        Page<MonitoringJob> jobs = monitoringJobService.findAll(normalizedSearch, pageable);

        model.addAttribute("jobs", jobs);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);

        return "monitoring/jobs :: jobsTable";
    }

    /*
     * CREATE MONITORING JOB
     */
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    @GetMapping("/new")
    public String createJobForm(@RequestParam(required = false) String mode, Model model)
    {
        MonitoringJob job = new MonitoringJob();
        boolean streamingContext = "STREAMING".equalsIgnoreCase(mode);
        if (streamingContext)
        {
            job.setExecutionMode(MonitoringJob.ExecutionMode.STREAMING);
        }

        job.setEnabled(true);
        job.setTimeoutSeconds(30);
        job.setRetryCount(0);
        job.setRetryDelaySeconds(5);
        job.setRecoveryEnabled(true);
        job.setStoreResult(true);
        job.setManualRunEnabled(true);
        job.setAllowConcurrentExecution(false);
        job.setExecutionMode(streamingContext ? MonitoringJob.ExecutionMode.STREAMING : MonitoringJob.ExecutionMode.STANDARD);
        job.setMaxStreamingRuntimeSeconds(300);

        model.addAttribute("job", job);
        model.addAttribute("machines", machineRepository.findAll());
        model.addAttribute("schedules", scheduleRepository.findAll());
        model.addAttribute("applications", applicationRepository.findAll());
        model.addAttribute("environments", environmentRepository.findAll());
        model.addAttribute("maxStreamingRuntimeSeconds", configuredMaximumStreamingRuntimeSeconds);
        model.addAttribute("executionModeLocked", streamingContext);
        model.addAttribute("pageTitle", streamingContext ? "Add Streaming Job" : "Add Monitoring Job");
        model.addAttribute("submitLabel", "Save Job");

        return "monitoring/job-form";
    }

    /*
     * CREATE / UPDATE MONITORING JOB
     */
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    @PostMapping
    public String saveJob(@ModelAttribute("job") MonitoringJob job, @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long environmentId, @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) Long scheduleId, @RequestParam(required = false) String mode,
            Authentication authentication, RedirectAttributes redirectAttributes)
    {
        // The form context determines the mode; never trust a disabled client-side control.
        job.setExecutionMode("STREAMING".equalsIgnoreCase(mode)
                ? MonitoringJob.ExecutionMode.STREAMING
                : MonitoringJob.ExecutionMode.STANDARD);
        if (job.getMaxStreamingRuntimeSeconds() == null)
        {
            job.setMaxStreamingRuntimeSeconds(300);
        }

        String validationError = validateJobConfiguration(job, applicationId, environmentId);
        if (validationError == null
                && job.getExecutionMode() == MonitoringJob.ExecutionMode.STREAMING
                && (job.getMaxStreamingRuntimeSeconds() < 1
                    || job.getMaxStreamingRuntimeSeconds() > configuredMaximumStreamingRuntimeSeconds))
        {
            validationError = "Maximum streaming runtime must be between 1 and "
                    + configuredMaximumStreamingRuntimeSeconds + " seconds.";
        }
        else if (validationError == null
                && job.getExecutionMode() == MonitoringJob.ExecutionMode.STREAMING
                && job.getType() != MonitoringJob.MonitorType.SCRIPT)
        {
            validationError = "Streaming execution currently supports Script monitoring jobs only.";
        }
        if (validationError != null)
        {
            redirectAttributes.addFlashAttribute("errorMessage", validationError);
            String modeSuffix = "STREAMING".equalsIgnoreCase(mode) ? "?mode=STREAMING" : "";
            return job.getId() == null ? "redirect:/monitoring/jobs/new" + modeSuffix
                    : "redirect:/monitoring/jobs/" + job.getId() + "/edit" + modeSuffix;
        }

        // Streaming jobs are on-demand only; do not persist a schedule for them.
        Long resolvedScheduleId = job.getExecutionMode() == MonitoringJob.ExecutionMode.STREAMING
                ? null : scheduleId;

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow(
                () -> new IllegalArgumentException("Logged-in user not found: " + authentication.getName()));

        if (job.getId() != null)
        {
            /*
             * EDIT EXISTING JOB
             */

            MonitoringJob existingJob = monitoringJobService.findById(job.getId());

            // Basic information
            existingJob.setName(job.getName());
            existingJob.setDescription(job.getDescription());
            existingJob.setType(job.getType());
            existingJob.setSeverity(job.getSeverity());
            existingJob.setEnabled(job.isEnabled());
            existingJob.setStoreResult(job.isStoreResult());
            existingJob.setManualRunEnabled(job.isManualRunEnabled());
            existingJob.setAllowConcurrentExecution(job.isAllowConcurrentExecution());
            existingJob.setExecutionMode(job.getExecutionMode());
            existingJob.setMaxStreamingRuntimeSeconds(job.getMaxStreamingRuntimeSeconds());

            // Monitoring configuration
            existingJob.setTimeoutSeconds(job.getTimeoutSeconds());
            existingJob.setRetryCount(job.getRetryCount());
            existingJob.setRetryDelaySeconds(job.getRetryDelaySeconds());
            existingJob.setExpectedResult(job.getExpectedResult());
            existingJob.setFailureMessage(job.getFailureMessage());
            existingJob.setRecoveryEnabled(job.isRecoveryEnabled());

            // Script configuration
            existingJob.setScriptPath(job.getScriptPath());
            existingJob.setWorkingDirectory(job.getWorkingDirectory());
            existingJob.setCommandArguments(job.getCommandArguments());

            // API configuration
            existingJob.setHttpMethod(job.getHttpMethod());
            existingJob.setUrl(job.getUrl());
            existingJob.setRequestHeaders(job.getRequestHeaders());
            existingJob.setRequestBody(job.getRequestBody());
            existingJob.setExpectedHttpStatus(job.getExpectedHttpStatus());
            existingJob.setExpectedResponse(job.getExpectedResponse());

            // Health check configuration
            existingJob.setHealthCheckType(job.getHealthCheckType());
            existingJob.setTargetHost(job.getTargetHost());
            existingJob.setTargetPort(job.getTargetPort());

            // Machine
            if (machineId != null)
            {
                existingJob.setMachine(machineRepository.findById(machineId)
                        .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId)));
            } else
            {
                existingJob.setMachine(null);
            }

            // Schedule
            if (resolvedScheduleId != null)
            {
                existingJob.setSchedule(scheduleRepository.findById(resolvedScheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + resolvedScheduleId)));
            } else
            {
                existingJob.setSchedule(null);
            }

            // Application
            if (applicationId != null)
            {
                existingJob.setApplication(applicationRepository.findById(applicationId)
                        .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId)));
            } else
            {
                existingJob.setApplication(null);
            }

            // Environment
            if (environmentId != null)
            {
                existingJob.setEnvironment(environmentRepository.findById(environmentId)
                        .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentId)));
            } else
            {
                existingJob.setEnvironment(null);
            }

            // Audit
            existingJob.setUpdatedBy(currentUser.getId());

            monitoringJobService.save(existingJob);
        } else
        {
            /*
             * CREATE NEW JOB
             */

            // Machine
            if (machineId != null)
            {
                job.setMachine(machineRepository.findById(machineId)
                        .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machineId)));
            }

            // Schedule
            if (resolvedScheduleId != null)
            {
                job.setSchedule(scheduleRepository.findById(resolvedScheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + resolvedScheduleId)));
            }

            // Application
            if (applicationId != null)
            {
                job.setApplication(applicationRepository.findById(applicationId)
                        .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId)));
            }

            // Environment
            if (environmentId != null)
            {
                job.setEnvironment(environmentRepository.findById(environmentId)
                        .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentId)));
            }

            // Audit
            job.setCreatedBy(currentUser.getId());
            job.setUpdatedBy(currentUser.getId());

            monitoringJobService.save(job);
        }

        return "STREAMING".equalsIgnoreCase(mode)
                ? "redirect:/monitoring/streaming-jobs"
                : "redirect:/monitoring/jobs";
    }

    private String validateJobConfiguration(MonitoringJob job, Long applicationId, Long environmentId)
    {
        if (job.getName() == null || job.getName().isBlank())
            return "Job name is required.";
        if (job.getName().trim().length() > 150)
            return "Job name must not exceed 150 characters.";
        if (job.getDescription() != null && job.getDescription().length() > 500)
            return "Description must not exceed 500 characters.";
        if (job.getSeverity() == null)
            return "Severity is required.";
        if (applicationId == null)
            return "Select an application.";
        if (environmentId == null)
            return "Select an environment.";
        if (job.getType() == null)
            return "Select a monitoring type.";
        if (job.getTimeoutSeconds() == null || job.getTimeoutSeconds() < 1)
            return "Timeout must be at least 1 second.";
        if (job.getRetryCount() == null || job.getRetryCount() < 0)
            return "Retry count must be zero or greater.";
        if (job.getRetryDelaySeconds() == null || job.getRetryDelaySeconds() < 0)
            return "Retry delay must be zero or greater.";
        if (job.getExpectedHttpStatus() != null
                && (job.getExpectedHttpStatus() < 100 || job.getExpectedHttpStatus() > 599))
            return "Expected HTTP status must be between 100 and 599.";

        if (job.getType() == MonitoringJob.MonitorType.SCRIPT)
        {
            if (job.getScriptPath() == null || job.getScriptPath().isBlank())
                return "Script path is required for Script monitoring jobs.";
            if (job.getScriptPath().trim().length() > 1000)
                return "Script path must not exceed 1000 characters.";
            if (job.getWorkingDirectory() != null && job.getWorkingDirectory().length() > 1000)
                return "Working directory must not exceed 1000 characters.";
        }
        else if (job.getType() == MonitoringJob.MonitorType.API)
        {
            if (job.getHttpMethod() == null || job.getHttpMethod().isBlank())
                return "Select an HTTP method for API monitoring.";
            String method = job.getHttpMethod().trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("GET", "POST", "PUT", "DELETE").contains(method))
                return "HTTP method must be GET, POST, PUT or DELETE.";
            if (job.getUrl() == null || job.getUrl().isBlank())
                return "URL is required for API monitoring.";
            if (job.getUrl().trim().length() > 2000)
                return "URL must not exceed 2000 characters.";
            try
            {
                URI uri = URI.create(job.getUrl().trim());
                if (uri.getHost() == null || uri.getHost().isBlank()
                        || uri.getScheme() == null
                        || !("http".equalsIgnoreCase(uri.getScheme())
                                || "https".equalsIgnoreCase(uri.getScheme())))
                    return "Enter a valid HTTP or HTTPS URL.";
            }
            catch (IllegalArgumentException exception)
            {
                return "Enter a valid HTTP or HTTPS URL.";
            }
        }
        else if (job.getType() == MonitoringJob.MonitorType.HEALTH_CHECK)
        {
            if (job.getHealthCheckType() == null || job.getHealthCheckType().isBlank())
                return "Select a health check type.";
            String checkType = job.getHealthCheckType().trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("TCP", "HTTP", "PING").contains(checkType))
                return "Health check type must be TCP, HTTP or PING.";
            if (job.getTargetHost() == null || job.getTargetHost().isBlank())
                return "Target host is required for health checks.";
            if (job.getTargetHost().trim().length() > 255)
                return "Target host must not exceed 255 characters.";
            if (("TCP".equals(checkType) || "HTTP".equals(checkType))
                    && (job.getTargetPort() == null || job.getTargetPort() < 1 || job.getTargetPort() > 65535))
                return "Target port must be between 1 and 65535 for TCP and HTTP health checks.";
        }

        return null;
    }

    /*
     * VIEW
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String viewJob(@PathVariable Long id, Model model)
    {
        MonitoringJob job = monitoringJobService.findById(id);

        model.addAttribute("job", job);

        return "monitoring/job-view";
    }

    /*
     * EDIT
     */
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    @GetMapping("/{id}/edit")
    public String editJobForm(@PathVariable Long id, @RequestParam(required = false) String mode, Model model)
    {
        MonitoringJob job = monitoringJobService.findById(id);
        boolean streamingContext = "STREAMING".equalsIgnoreCase(mode)
                && job.getExecutionMode() == MonitoringJob.ExecutionMode.STREAMING;

        model.addAttribute("job", job);
        model.addAttribute("machines", machineRepository.findAll());
        model.addAttribute("schedules", scheduleRepository.findAll());
        model.addAttribute("applications", applicationRepository.findAll());
        model.addAttribute("environments", environmentRepository.findAll());
        model.addAttribute("maxStreamingRuntimeSeconds", configuredMaximumStreamingRuntimeSeconds);
        model.addAttribute("executionModeLocked", streamingContext);
        model.addAttribute("pageTitle", streamingContext ? "Edit Streaming Job" : "Edit Monitoring Job");
        model.addAttribute("submitLabel", "Update Job");

        return "monitoring/job-form";
    }

    /*
     * ENABLE / DISABLE
     */
    @PreAuthorize("hasAuthority('MONITORING_CONFIG')")
    @PostMapping("/{id}/toggle")
    public String toggleEnabled(@PathVariable Long id)
    {
        monitoringJobService.toggleEnabled(id);

        return "redirect:/monitoring/jobs";
    }

    /*
     * DELETE
     */
    @PostMapping("/{id}/delete")
    public String deleteJob(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        String jobName = monitoringJobService.delete(id);

        redirectAttributes.addFlashAttribute("successMessage",
                "Monitoring job '" + jobName + "' was deleted successfully.");

        return "redirect:/monitoring/jobs";
    }

    @PreAuthorize("hasAuthority('MONITORING_RUN')")
    @PostMapping("/{id}/run")
    public String runNow(@PathVariable Long id)
    {
        MonitoringJob job = monitoringJobService.findById(id);

        if (job.getExecutionMode() == MonitoringJob.ExecutionMode.STREAMING)
        {
            throw new IllegalStateException("Streaming jobs must be started from the Streaming Jobs page.");
        }

        if (!job.isManualRunEnabled())
        {
            throw new IllegalStateException("Manual execution is disabled for this monitoring job.");
        }

        monitoringExecutionManager.execute(job);

        return "redirect:/monitoring/jobs/" + id;
    }

    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String executionHistory(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search, Model model)
    {
        if (page < 0)
        {
            page = 0;
        }

        if (size != 5 && size != 10 && size != 25)
        {
            size = 5;
        }

        String normalizedSearch = search == null ? "" : search.trim();

        Pageable pageable = PageRequest.of(page, size);

        Page<MonitoringExecution> executions = monitoringExecutionService.findByJobId(id, normalizedSearch, pageable);

        Map<Long, MonitoringResult.ResultStatus> resultStatuses =
            monitoringExecutionService.findOverallResultStatuses(executions.getContent());

        model.addAttribute("executions", executions);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("resultStatuses", resultStatuses);
        model.addAttribute("jobId", id);

        return "monitoring/execution-history :: executionHistory";
    }

    @GetMapping("/{id}/executions/table")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String executionHistoryTable(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search, Model model)
    {
        if (page < 0)
        {
            page = 0;
        }

        if (size != 5 && size != 10 && size != 25)
        {
            size = 5;
        }

        String normalizedSearch = search == null ? "" : search.trim();

        Pageable pageable = PageRequest.of(page, size);

        Page<MonitoringExecution> executions = monitoringExecutionService.findByJobId(id, normalizedSearch, pageable);

        Map<Long, MonitoringResult.ResultStatus> resultStatuses =
            monitoringExecutionService.findOverallResultStatuses(executions.getContent());

        model.addAttribute("executions", executions);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("resultStatuses", resultStatuses);
        model.addAttribute("jobId", id);

        return "monitoring/execution-history :: executionHistory";
    }

    @GetMapping("/{jobId}/executions/{executionId}")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String executionDetails(@PathVariable Long jobId, @PathVariable Long executionId, Model model)
    {
        MonitoringExecution execution = monitoringExecutionService.findById(executionId);

        List<MonitoringResult> results = monitoringExecutionService.findResultsByExecutionId(executionId);

        model.addAttribute("execution", execution);
        model.addAttribute("results", results);

        return "monitoring/execution-details :: executionDetails";
    }
}