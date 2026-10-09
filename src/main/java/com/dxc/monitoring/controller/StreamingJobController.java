package com.dxc.monitoring.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dxc.monitoring.service.StreamingJobService;
import com.dxc.monitoring.service.StreamingJobService.StreamingJobView;

@Controller
public class StreamingJobController
{
    private final StreamingJobService streamingJobService;

    public StreamingJobController(StreamingJobService streamingJobService)
    {
        this.streamingJobService = streamingJobService;
    }

    @GetMapping("/monitoring/streaming-jobs")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public String page(Model model)
    {
        model.addAttribute("currentPage", "streaming-jobs");
        model.addAttribute("pageSize", 5);
        model.addAttribute("search", "");
        return "monitoring/streaming-jobs";
    }

    @GetMapping("/api/streaming-jobs")
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public List<StreamingJobView> list()
    {
        return streamingJobService.listJobs();
    }

    @PostMapping("/api/streaming-jobs/{id}/start")
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_RUN')")
    public List<StreamingJobView> start(@PathVariable Long id, Principal principal)
    {
        streamingJobService.start(id, principal.getName());
        return streamingJobService.listJobs();
    }

    @PostMapping("/api/streaming-jobs/{id}/stop")
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_RUN')")
    public List<StreamingJobView> stop(@PathVariable Long id)
    {
        streamingJobService.stop(id);
        return streamingJobService.listJobs();
    }

    @GetMapping(value = "/api/streaming-jobs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public SseEmitter events(@PathVariable Long id)
    {
        return streamingJobService.subscribe(id);
    }
}
