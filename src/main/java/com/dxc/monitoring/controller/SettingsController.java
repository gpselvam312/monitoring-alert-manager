package com.dxc.monitoring.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.SystemSettingService;
import com.dxc.monitoring.web.PageRequestSupport;

@Controller
@RequestMapping("/administration/settings")
@PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
public class SettingsController {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "settingKey", "settingKey", "description", "description",
            "settingValue", "settingValue", "dataType", "dataType");
    private final SystemSettingService systemSettingService;
    private final PageRequestSupport pageRequestSupport;

    public SettingsController(SystemSettingService systemSettingService, PageRequestSupport pageRequestSupport) {
        this.systemSettingService = systemSettingService;
        this.pageRequestSupport = pageRequestSupport;
    }

    @GetMapping
    public String settings(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "settingKey") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        addSettingsTableModel(model, page, size, search, sort, direction);
        model.addAttribute("currentPage", "settings");
        return "administration/settings";
    }

    @GetMapping("/table")
    public String settingsTable(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "settingKey") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        addSettingsTableModel(model, page, size, search, sort, direction);
        return "administration/settings :: settingsTable";
    }

    private void addSettingsTableModel(Model model, int page, int size, String search,
            String sort, String direction) {
        String normalizedSearch = search == null ? "" : search.trim();
        var spec = pageRequestSupport.create(page, size, sort, direction, "settingKey", SORT_FIELDS);
        model.addAttribute("settings", systemSettingService.findPage(normalizedSearch, spec.pageable()));
        model.addAttribute("pageSize", spec.pageSize());
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("sort", spec.sort());
        model.addAttribute("direction", spec.direction());
    }

    @PostMapping
    public String saveSettings(@RequestParam Map<String, String> requestParameters,
            Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Map<String, String> values = requestParameters.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("value_"))
                    .collect(Collectors.toMap(entry -> entry.getKey().substring("value_".length()),
                            Map.Entry::getValue, (existing, replacement) -> replacement,
                            java.util.LinkedHashMap::new));
            systemSettingService.updateSettings(values, userDetails.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/administration/settings";
    }
}
