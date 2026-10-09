package com.dxc.monitoring.controller;

import java.util.Map;

import org.springframework.scheduling.support.CronExpression;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.Schedule;
import com.dxc.monitoring.service.ScheduleService;

@Controller
@RequestMapping("/scheduling/schedules")
public class ScheduleController
{

    private final ScheduleService scheduleService;
    private static final Map<String, String> SCHEDULE_SORT_FIELDS =
        Map.of("name", "name", "type", "type", "enabled", "enabled");

    public ScheduleController(ScheduleService scheduleService)
    {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCHEDULE_VIEW')")
    public String listSchedules(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
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

        Sort pageableSort = buildScheduleSort(sort, direction);

        Pageable pageable = PageRequest.of(page, size, pageableSort);

        Page<Schedule> schedules = scheduleService.findAll(normalizedSearch, pageable);

        model.addAttribute("schedules", schedules);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("currentPage", "schedules");

        return "scheduling/schedules";
    }

    @GetMapping("/table")
    @PreAuthorize("hasAuthority('SCHEDULE_VIEW')")
    public String scheduleTable(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
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

        Sort pageableSort = buildScheduleSort(sort, direction);

        Pageable pageable = PageRequest.of(page, size, pageableSort);

        Page<Schedule> schedules = scheduleService.findAll(normalizedSearch, pageable);

        model.addAttribute("schedules", schedules);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);

        return "scheduling/schedules :: schedulesTable";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SCHEDULE_CONFIG')")
    public String createScheduleForm(Model model)
    {
        Schedule schedule = new Schedule();
        schedule.setEnabled(true);

        model.addAttribute("schedule", schedule);
        model.addAttribute("pageTitle", "Add Schedule");
        model.addAttribute("submitLabel", "Save Schedule");

        return "scheduling/schedule-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCHEDULE_CONFIG')")
    public String saveSchedule(@ModelAttribute("schedule") Schedule schedule, Model model)
    {
        String validationError = validateSchedule(schedule);
        if (validationError != null)
        {
            model.addAttribute("pageTitle", schedule.getId() == null ? "Add Schedule" : "Edit Schedule");
            model.addAttribute("submitLabel", schedule.getId() == null ? "Save Schedule" : "Update Schedule");
            model.addAttribute("formError", validationError);
            model.addAttribute("currentPage", "schedules");
            return "scheduling/schedule-form";
        }

        if (schedule.getId() != null)
        {
            Schedule existingSchedule = scheduleService.findById(schedule.getId());

            existingSchedule.setName(schedule.getName());
            existingSchedule.setDescription(schedule.getDescription());
            existingSchedule.setType(schedule.getType());
            existingSchedule.setCronExpression(schedule.getCronExpression());
            existingSchedule.setFixedDelaySeconds(schedule.getFixedDelaySeconds());
            existingSchedule.setFixedRateSeconds(schedule.getFixedRateSeconds());
            existingSchedule.setStartTime(schedule.getStartTime());
            existingSchedule.setEndTime(schedule.getEndTime());
            existingSchedule.setEnabled(schedule.isEnabled());

            scheduleService.save(existingSchedule);
        } else
        {
            scheduleService.save(schedule);
        }

        return "redirect:/scheduling/schedules";
    }

    private String validateSchedule(Schedule schedule)
    {
        String name = schedule.getName() == null ? "" : schedule.getName().trim();
        schedule.setName(name);

        if (name.isEmpty())
        {
            return "Schedule name is required.";
        }
        if (name.length() > 100)
        {
            return "Schedule name must be 100 characters or fewer.";
        }
        if (schedule.getDescription() != null && schedule.getDescription().length() > 500)
        {
            return "Description must be 500 characters or fewer.";
        }

        boolean duplicateName = schedule.getId() == null
                ? scheduleService.nameExists(name)
                : scheduleService.nameExistsForAnotherSchedule(name, schedule.getId());
        if (duplicateName)
        {
            return "A schedule with this name already exists.";
        }

        if (schedule.getType() == null)
        {
            return "Select a schedule type.";
        }

        String cron = schedule.getCronExpression() == null ? "" : schedule.getCronExpression().trim();
        schedule.setCronExpression(cron.isEmpty() ? null : cron);

        switch (schedule.getType())
        {
            case CRON:
                if (cron.isEmpty())
                {
                    return "Cron Expression is required for a Cron schedule.";
                }
                if (schedule.getFixedDelaySeconds() != null || schedule.getFixedRateSeconds() != null)
                {
                    return "A Cron schedule must not contain Fixed Delay or Fixed Rate settings.";
                }
                try
                {
                    CronExpression.parse(cron);
                }
                catch (IllegalArgumentException exception)
                {
                    return "Enter a valid Spring cron expression (second minute hour day-of-month month day-of-week).";
                }
                break;
            case FIXED_DELAY:
                if (schedule.getFixedDelaySeconds() == null || schedule.getFixedDelaySeconds() < 1)
                {
                    return "Fixed Delay must be at least 1 second.";
                }
                if (!cron.isEmpty() || schedule.getFixedRateSeconds() != null)
                {
                    return "A Fixed Delay schedule must not contain Cron or Fixed Rate settings.";
                }
                break;
            case FIXED_RATE:
                if (schedule.getFixedRateSeconds() == null || schedule.getFixedRateSeconds() < 1)
                {
                    return "Fixed Rate must be at least 1 second.";
                }
                if (!cron.isEmpty() || schedule.getFixedDelaySeconds() != null)
                {
                    return "A Fixed Rate schedule must not contain Cron or Fixed Delay settings.";
                }
                break;
            default:
                return "Select a supported schedule type.";
        }

        if ((schedule.getStartTime() == null) != (schedule.getEndTime() == null))
        {
            return "Start Time and End Time must either both be set or both be empty.";
        }
        if (schedule.getStartTime() != null && schedule.getStartTime().equals(schedule.getEndTime()))
        {
            return "Start Time and End Time cannot be the same.";
        }

        if (schedule.getDescription() != null)
        {
            String description = schedule.getDescription().trim();
            schedule.setDescription(description.isEmpty() ? null : description);
        }

        return null;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCHEDULE_VIEW')")
    public String viewSchedule(@PathVariable Long id, Model model)
    {
        Schedule schedule = scheduleService.findById(id);

        model.addAttribute("schedule", schedule);

        return "scheduling/schedule-view";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('SCHEDULE_CONFIG')")
    public String editScheduleForm(@PathVariable Long id, Model model)
    {
        Schedule schedule = scheduleService.findById(id);

        model.addAttribute("schedule", schedule);
        model.addAttribute("pageTitle", "Edit Schedule");
        model.addAttribute("submitLabel", "Update Schedule");

        return "scheduling/schedule-form";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('SCHEDULE_CONFIG')")
    public String toggleEnabled(@PathVariable Long id)
    {
        scheduleService.toggleEnabled(id);

        return "redirect:/scheduling/schedules";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('SCHEDULE_CONFIG')")
    public String deleteSchedule(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        String scheduleName = scheduleService.delete(id);

        redirectAttributes.addFlashAttribute("successMessage",
                "Schedule '" + scheduleName + "' was deleted successfully.");

        return "redirect:/scheduling/schedules";
    }

    private Sort buildScheduleSort(String sort, String direction)
    {
        String sortField = SCHEDULE_SORT_FIELDS.getOrDefault(sort, "name");

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return Sort.by(sortDirection, sortField);
    }
}
