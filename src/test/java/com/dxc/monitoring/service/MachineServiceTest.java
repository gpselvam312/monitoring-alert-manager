package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.dxc.monitoring.entity.Machine;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.MachineRepository;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;

class MachineServiceTest
{
    @Test
    void rejectsMachineWithoutApplicationEnvironment()
    {
        MachineRepository machines = mock(MachineRepository.class);
        MachineService service = new MachineService(machines, mock(EnvironmentRepository.class),
                mock(DashboardAccessService.class));
        Machine machine = new Machine();
        machine.setId(10L);
        when(machines.findById(10L)).thenReturn(Optional.of(machine));

        assertThrows(AccessDeniedException.class, () -> service.findById(10L));
    }

    @Test
    void scopesMachineLookupToItsEnvironmentApplication()
    {
        MachineRepository machines = mock(MachineRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        Machine machine = new Machine();
        machine.setId(10L);
        com.dxc.monitoring.entity.Application application = new com.dxc.monitoring.entity.Application();
        application.setId(4L);
        com.dxc.monitoring.entity.Environment environment = new com.dxc.monitoring.entity.Environment();
        environment.setId(8L);
        environment.setApplication(application);
        machine.setEnvironment(environment);
        when(machines.findById(10L)).thenReturn(Optional.of(machine));

        MachineService service = new MachineService(machines, mock(EnvironmentRepository.class), access);
        assertEquals(machine, service.findById(10L));
        verify(access).assertCanAccessEnvironment(4L, 8L, "MACHINE_VIEW");
    }
    @Test
    void usesMonitoringConfigurationPermissionWhenSelectingMachineForJob() {
        MachineRepository machines = mock(MachineRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        Machine machine = machineInEnvironment(10L, 20L, 30L);
        when(machines.findByIdWithEnvironment(10L)).thenReturn(Optional.of(machine));

        MachineService service = new MachineService(machines, mock(EnvironmentRepository.class), access);
        machine.setEnabled(true);
        machine.getEnvironment().setEnabled(true);
        assertEquals(machine, service.findByIdForMonitoringJobConfiguration(10L));
        verify(access).assertCanAccessEnvironment(20L, 30L, "MONITORING_CONFIG");
    }

    @Test
    void allowsMachineConfigurationWithoutRequiringViewPermission() {
        MachineRepository machines = mock(MachineRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        Machine machine = machineInEnvironment(10L, 20L, 30L);
        when(machines.findByIdWithEnvironment(10L)).thenReturn(Optional.of(machine));

        MachineService service = new MachineService(machines, mock(EnvironmentRepository.class), access);
        assertEquals(machine, service.findByIdForMachineConfiguration(10L));
        verify(access).assertCanAccessEnvironment(20L, 30L, "MACHINE_CONFIG");
    }

    @Test
    void deleteRequiresMachineConfigurationPermissionNotViewPermission() {
        MachineRepository machines = mock(MachineRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        Machine machine = machineInEnvironment(10L, 20L, 30L);
        machine.setName("test-machine");
        when(machines.findByIdWithEnvironment(10L)).thenReturn(Optional.of(machine));

        MachineService service = new MachineService(machines, mock(EnvironmentRepository.class), access);
        assertEquals("test-machine", service.delete(10L));
        verify(access).assertCanAccessEnvironment(20L, 30L, "MACHINE_CONFIG");
        verify(machines).delete(machine);
    }

    private Machine machineInEnvironment(Long machineId, Long applicationId, Long environmentId) {
        Machine machine = new Machine();
        machine.setId(machineId);
        com.dxc.monitoring.entity.Application application = new com.dxc.monitoring.entity.Application();
        application.setId(applicationId);
        com.dxc.monitoring.entity.Environment environment = new com.dxc.monitoring.entity.Environment();
        environment.setId(environmentId);
        environment.setApplication(application);
        machine.setEnvironment(environment);
        return machine;
    }

}
