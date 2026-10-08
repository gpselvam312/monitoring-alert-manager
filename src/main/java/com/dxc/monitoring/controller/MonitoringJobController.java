package com.dxc.monitoring.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private static final Map<String, String> JOB_SORT_FIELDS =
        Map.of("name", "name", "application.name", "application.name", "environment.name", "environment.name",
                "machine.name", "machine.name", "type", "type", "severity", "severity", "schedule.name",
                "schedule.name", "enabled", "enabled", "dashboardEnabled", "dashboardEnabled");

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
    public String createJobForm(Model model)
    {
        MonitoringJob job = new MonitoringJob();

        job.setEnabled(true);
        job.setTimeoutSeconds(30);
        job.setRetryCount(0);
        job.setRetryDelaySeconds(5);
        job.setRecoveryEnabled(true);
        job.setStoreResult(true);
        job.setManualRunEnabled(true);
        job.setAllowConcurrentExecution(false);

        model.addAttribute("job", job);
        model.addAttribute("machines", machineRepository.findAll());
        model.addAttribute("schedules", scheduleRepository.findAll());
        model.addAttribute("applications", applicationRepository.findAll());
        model.addAttribute("environments", environmentRepository.findAll());
        model.addAttribute("pageTitle", "Add Monitoring Job");
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
            @RequestParam(required = false) Long scheduleId, Authentication authentication)
    {
        System.out.println("DEBUG MonitoringJob POST - id = " + job.getId() + ", name = " + job.getName());

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
            if (scheduleId != null)
            {
                existingJob.setSchedule(scheduleRepository.findById(scheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId)));
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
            if (scheduleId != null)
            {
                job.setSchedule(scheduleRepository.findById(scheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId)));
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

        return "redirect:/monitoring/jobs";
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
    public String editJobForm(@PathVariable Long id, Model model)
    {
        MonitoringJob job = monitoringJobService.findById(id);

        model.addAttribute("job", job);
        model.addAttribute("machines", machineRepository.findAll());
        model.addAttribute("schedules", scheduleRepository.findAll());
        model.addAttribute("applications", applicationRepository.findAll());
        model.addAttribute("environments", environmentRepository.findAll());
        model.addAttribute("pageTitle", "Edit Monitoring Job");
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