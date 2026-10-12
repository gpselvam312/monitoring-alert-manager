package com.dxc.monitoring.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.SystemSetting;
import com.dxc.monitoring.security.CustomUserDetails;
import com.dxc.monitoring.service.SystemSettingService;

@Controller
@RequestMapping("/administration/settings")
@PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
public class SettingsController
{
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "settingKey", "settingKey",
            "description", "description",
            "settingValue", "settingValue",
            "dataType", "dataType");

    private final SystemSettingService systemSettingService;

    public SettingsController(SystemSettingService systemSettingService)
    {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public String settings(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "settingKey") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        addSettingsTableModel(model, page, size, search, sort, direction);
        model.addAttribute("currentPage", "settings");
        return "administration/settings";
    }

    @GetMapping("/table")
    public String settingsTable(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "settingKey") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        addSettingsTableModel(model, page, size, search, sort, direction);
        return "administration/settings :: settingsTable";
    }

    private void addSettingsTableModel(Model model, int page, int size, String search, String sort,
            String direction)
    {
        int pageSize = size == 10 || size == 25 ? size : 5;
        String normalizedSearch = search == null ? "" : search.trim();
        String sortField = SORT_FIELDS.getOrDefault(sort, "settingKey");
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        String query = normalizedSearch.toLowerCase(Locale.ROOT);
        List<SystemSetting> filteredSettings = systemSettingService.findAll().stream()
                .filter(setting -> query.isEmpty()
                        || contains(setting.getSettingKey(), query)
                        || contains(setting.getDescription(), query)
                        || contains(setting.getSettingValue(), query)
                        || contains(setting.getDataType(), query))
                .sorted(Comparator.comparing(setting -> sortValue(setting, sortField),
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (sortDirection == Sort.Direction.DESC)
        {
            java.util.Collections.reverse(filteredSettings);
        }

        int total = filteredSettings.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int normalizedPage = Math.max(0, page);
        if (totalPages > 0 && normalizedPage >= totalPages)
        {
            normalizedPage = totalPages - 1;
        }
        if (totalPages == 0)
        {
            normalizedPage = 0;
        }

        int start = Math.min(normalizedPage * pageSize, total);
        int end = Math.min(start + pageSize, total);
        Pageable pageable = PageRequest.of(normalizedPage, pageSize);
        Page<SystemSetting> settings = new PageImpl<>(filteredSettings.subList(start, end), pageable, total);

        model.addAttribute("settings", settings);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("sort", sortField);
        model.addAttribute("direction", sortDirection.name().toLowerCase(Locale.ROOT));
    }

    private boolean contains(String value, String query)
    {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String sortValue(SystemSetting setting, String field)
    {
        return switch (field)
        {
            case "description" -> setting.getDescription() == null ? "" : setting.getDescription();
            case "settingValue" -> setting.getSettingValue() == null ? "" : setting.getSettingValue();
            case "dataType" -> setting.getDataType() == null ? "" : setting.getDataType();
            default -> setting.getSettingKey() == null ? "" : setting.getSettingKey();
        };
    }

    @PostMapping
    public String saveSettings(@RequestParam Map<String, String> requestParameters,
            Authentication authentication, RedirectAttributes redirectAttributes)
    {
        try
        {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Map<String, String> values = requestParameters.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("value_"))
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().substring("value_".length()),
                            Map.Entry::getValue,
                            (existing, replacement) -> replacement,
                            java.util.LinkedHashMap::new));

            systemSettingService.updateSettings(values, userDetails.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully.");
        }
        catch (IllegalArgumentException exception)
        {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/administration/settings";
    }
}
