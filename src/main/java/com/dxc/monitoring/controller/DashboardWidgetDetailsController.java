package com.dxc.monitoring.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.entity.DashboardWidgetResult;
import com.dxc.monitoring.repository.DashboardWidgetResultRepository;
import com.dxc.monitoring.service.dashboard.DashboardService;
import com.dxc.monitoring.service.dashboard.WidgetResult;

@Controller
public class DashboardWidgetDetailsController
{
    private final DashboardService dashboardService;
    private final DashboardWidgetResultRepository dashboardWidgetResultRepository;

    private static final Map<String, String> RESULT_SORT_FIELDS =
        Map.of("resultTime", "resultTime", "status", "status", "message", "message");

    public DashboardWidgetDetailsController(DashboardService dashboardService,
            DashboardWidgetResultRepository dashboardWidgetResultRepository)
    {
        this.dashboardService = dashboardService;
        this.dashboardWidgetResultRepository = dashboardWidgetResultRepository;
    }

    @GetMapping("/dashboard/widgets/{id}/details")
    public String details(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "resultTime") String sort,
            @RequestParam(defaultValue = "desc") String direction, Model model)
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

        Sort pageableSort = buildResultSort(sort, direction);

        Pageable pageable = PageRequest.of(page, size, pageableSort);

        DashboardWidget widget = dashboardService.getWidget(id);

        Page<DashboardWidgetResult> history =
            dashboardWidgetResultRepository.findAllForDetails(id, normalizedSearch, pageable);
        Map<Long, WidgetResult> parsedResults = new HashMap<>();

        for (DashboardWidgetResult result : history.getContent())
        {
            parsedResults.put(result.getId(), dashboardService.getWidgetResult(result));
        }
        model.addAttribute("currentPage", "dashboard");
        model.addAttribute("parsedResults", parsedResults);
        model.addAttribute("widget", widget);
        model.addAttribute("history", history);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);

        return "dashboard-widget-details";
    }

    private Sort buildResultSort(String sort, String direction)
    {
        String sortField = RESULT_SORT_FIELDS.getOrDefault(sort, "resultTime");

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

        return Sort.by(sortDirection, sortField);
    }
}