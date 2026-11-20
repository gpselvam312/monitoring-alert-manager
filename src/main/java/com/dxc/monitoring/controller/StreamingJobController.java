package com.dxc.monitoring.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dxc.monitoring.service.StreamingJobService;
import com.dxc.monitoring.web.PageRequestSupport;
import com.dxc.monitoring.service.StreamingJobService.StreamingJobView;

@Controller
public class StreamingJobController
{
    private final StreamingJobService streamingJobService;
    private final PageRequestSupport pageRequestSupport;

    public StreamingJobController(StreamingJobService streamingJobService, PageRequestSupport pageRequestSupport)
    {
        this.streamingJobService = streamingJobService;
        this.pageRequestSupport = pageRequestSupport;
    }

    @GetMapping("/monitoring/streaming-jobs")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String page(Model model)
    {
        model.addAttribute("currentPage", "streaming-jobs");
        model.addAttribute("pageSize", pageRequestSupport.defaultPageSize());
        model.addAttribute("search", "");
        return "monitoring/streaming-jobs";
    }

    @GetMapping("/api/streaming-jobs")
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public Page<StreamingJobView> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction)
    {
        Map<String, String> allowedFields = Map.of(
                "name", "name", "application", "application.name", "environment", "environment.name",
                "machine", "machine.name", "type", "type", "severity", "severity",
                "schedule", "schedule.name", "enabled", "enabled");
        var spec = pageRequestSupport.create(page, size, sort, direction, "name", allowedFields);
        Pageable pageable = spec.pageable();
        return streamingJobService.listJobs(search, pageable);
    }

    private List<StreamingJobView> currentPage(int page, int size, String search, String sort, String direction) {
        Map<String, String> allowedFields = Map.of("name", "name", "application", "application.name",
                "environment", "environment.name", "machine", "machine.name", "type", "type",
                "severity", "severity", "schedule", "schedule.name", "enabled", "enabled");
        var spec = pageRequestSupport.create(page, size, sort, direction, "name", allowedFields);
        return streamingJobService.listJobs(search, spec.pageable()).getContent();
    }

    @PostMapping("/api/streaming-jobs/{id}/start")
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_RUN')")
    public List<StreamingJobView> start(@PathVariable Long id, Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction)
    {
        streamingJobService.start(id, principal.getName());
        return currentPage(page, size, search, sort, direction);
    }

    @PostMapping("/api/streaming-jobs/{id}/stop")
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_RUN')")
    public List<StreamingJobView> stop(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction)
    {
        streamingJobService.stop(id);
        return currentPage(page, size, search, sort, direction);
    }

    @GetMapping(value = "/api/streaming-jobs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public SseEmitter events(@PathVariable Long id)
    {
        return streamingJobService.subscribe(id);
    }
}
