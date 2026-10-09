package com.dxc.monitoring.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Schedule;
import com.dxc.monitoring.repository.ScheduleRepository;

@Service
public class ScheduleService
{

    private final ScheduleRepository scheduleRepository;
    private final MonitoringSchedulerService monitoringSchedulerService;

    public ScheduleService(ScheduleRepository scheduleRepository,
            MonitoringSchedulerService monitoringSchedulerService)
    {
        this.scheduleRepository = scheduleRepository;
        this.monitoringSchedulerService = monitoringSchedulerService;
    }

    @Transactional(readOnly = true)
    public Page<Schedule> findAll(String search, Pageable pageable)
    {
        return scheduleRepository.findAllForList(search, pageable);
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name)
    {
        return scheduleRepository.existsByNameIgnoreCase(name);
    }

    @Transactional(readOnly = true)
    public boolean nameExistsForAnotherSchedule(String name, Long id)
    {
        return scheduleRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    @Transactional(readOnly = true)
    public Schedule findById(Long id)
    {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
    }

    @Transactional
    public Schedule save(Schedule schedule)
    {
        Schedule saved = scheduleRepository.save(schedule);
        monitoringSchedulerService.refreshSchedule(saved.getId());
        return saved;
    }

    @Transactional
    public String delete(Long id)
    {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));

        String scheduleName = schedule.getName();

        monitoringSchedulerService.cancelSchedule(id);
        scheduleRepository.delete(schedule);

        return scheduleName;
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        Schedule schedule = findById(id);
        schedule.setEnabled(!schedule.isEnabled());
        scheduleRepository.save(schedule);
        monitoringSchedulerService.refreshSchedule(id);
    }
}