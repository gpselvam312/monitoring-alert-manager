package com.dxc.monitoring.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Machine;
import com.dxc.monitoring.repository.MachineRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;

@Service
public class MachineService
{

    private final MachineRepository machineRepository;
    private final EnvironmentRepository environmentRepository;
    private final DashboardAccessService accessService;

    public MachineService(MachineRepository machineRepository, EnvironmentRepository environmentRepository,
            DashboardAccessService accessService)
    {
        this.machineRepository = machineRepository;
        this.environmentRepository = environmentRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public Page<Machine> findAll(String search, Pageable pageable)
    {
        List<Long> applicationIds = accessService.getApplicationsWithPermission("MACHINE_VIEW")
                .stream().map(Application::getId).toList();
        if (applicationIds.isEmpty()) return Page.empty(pageable);
        return machineRepository.findAllForApplications(search, applicationIds, pageable);
    }

    @Transactional(readOnly = true)
    public List<Machine> findAllForMonitoringJobConfiguration() {
        List<Long> applicationIds = accessService.getApplicationsWithPermission("MONITORING_CONFIG")
                .stream().map(Application::getId).toList();
        return applicationIds.isEmpty() ? List.of() : machineRepository.findAllForApplications(applicationIds);
    }

    @Transactional(readOnly = true)
    public Machine findByIdForMachineConfiguration(Long id) {
        Machine machine = machineRepository.findByIdWithEnvironment(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        assertMachineAccess(machine, "MACHINE_CONFIG");
        return machine;
    }

    @Transactional(readOnly = true)
    public Machine findByIdForMonitoringJobConfiguration(Long id) {
        Machine machine = machineRepository.findByIdWithEnvironment(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        assertMachineAccess(machine, "MONITORING_CONFIG");
        if (!machine.isEnabled() || machine.getEnvironment() == null || !machine.getEnvironment().isEnabled())
            throw new IllegalArgumentException("Select an enabled machine in an enabled environment.");
        return machine;
    }

    @Transactional(readOnly = true)
    public Machine findById(Long id)
    {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        assertMachineAccess(machine, "MACHINE_VIEW");
        return machine;
    }

    @Transactional
    public Machine save(Machine machine)
    {
        if (machine.getEnvironment() == null || machine.getEnvironment().getId() == null)
            throw new IllegalArgumentException("An environment must be selected for the machine.");
        Environment environment = environmentRepository.findById(machine.getEnvironment().getId())
                .orElseThrow(() -> new IllegalArgumentException("Environment not found."));
        if (!environment.isEnabled() || environment.getApplication() == null)
            throw new IllegalArgumentException("Select an enabled environment assigned to an application.");
        accessService.assertCanAccessEnvironment(environment.getApplication().getId(),
                environment.getId(), "MACHINE_CONFIG");
        if (machine.getId() != null) {
            Machine existing = machineRepository.findById(machine.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + machine.getId()));
            assertMachineAccess(existing, "MACHINE_CONFIG");
        }
        machine.setEnvironment(environment);
        return machineRepository.save(machine);
    }

    @Transactional
    public String delete(Long id)
    {
        Machine machine = machineRepository.findByIdWithEnvironment(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        assertMachineAccess(machine, "MACHINE_CONFIG");

        String machineName = machine.getName();

        machineRepository.delete(machine);

        return machineName;
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        Machine machine = machineRepository.findByIdWithEnvironment(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        assertMachineAccess(machine, "MACHINE_CONFIG");
        machine.setEnabled(!machine.isEnabled());
        machineRepository.save(machine);
    }

    @Transactional(readOnly = true)
    public Machine findByIdWithEnvironment(Long id)
    {
        Machine machine = machineRepository.findByIdWithEnvironment(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
        assertMachineAccess(machine, "MACHINE_VIEW");
        return machine;
    }
    @Transactional(readOnly = true)
    public List<Environment> findEnabledEnvironments() {
        return accessService.getApplicationsWithPermission("MACHINE_CONFIG").stream()
                .flatMap(application -> accessService.getEnvironmentsForApplication(
                        application.getId(), "MACHINE_CONFIG").stream())
                .toList();
    }

    private void assertMachineAccess(Machine machine, String permission) {
        if (machine.getEnvironment() == null || machine.getEnvironment().getApplication() == null)
            throw new AccessDeniedException("Machine is not assigned to an application environment.");
        accessService.assertCanAccessEnvironment(machine.getEnvironment().getApplication().getId(),
                machine.getEnvironment().getId(), permission);
    }
}
