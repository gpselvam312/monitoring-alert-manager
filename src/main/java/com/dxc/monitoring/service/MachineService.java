package com.dxc.monitoring.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Machine;
import com.dxc.monitoring.repository.MachineRepository;

@Service
public class MachineService
{

    private final MachineRepository machineRepository;

    public MachineService(MachineRepository machineRepository)
    {
        this.machineRepository = machineRepository;
    }

    @Transactional(readOnly = true)
    public Page<Machine> findAll(String search, Pageable pageable)
    {
        return machineRepository.findAllForList(search, pageable);
    }

    @Transactional(readOnly = true)
    public Machine findById(Long id)
    {
        return machineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
    }

    @Transactional
    public Machine save(Machine machine)
    {
        return machineRepository.save(machine);
    }

    @Transactional
    public String delete(Long id)
    {
        Machine machine =
            machineRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));

        String machineName = machine.getName();

        machineRepository.delete(machine);

        return machineName;
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        Machine machine = findById(id);
        machine.setEnabled(!machine.isEnabled());
        machineRepository.save(machine);
    }

    @Transactional(readOnly = true)
    public Machine findByIdWithEnvironment(Long id)
    {
        return machineRepository.findByIdWithEnvironment(id)
                .orElseThrow(() -> new IllegalArgumentException("Machine not found: " + id));
    }
}