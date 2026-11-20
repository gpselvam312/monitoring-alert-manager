package com.dxc.monitoring.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.Machine;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.UserRepository;
import com.dxc.monitoring.service.MachineService;
import com.dxc.monitoring.web.PageRequestSupport;

@Controller
@RequestMapping("/monitoring/machines")
public class MachineController
{

    private final MachineService machineService;
    private final UserRepository userRepository;
    private final PageRequestSupport pageRequestSupport;
    private static final Map<String, String> MACHINE_SORT_FIELDS = Map.of("name", "name", "hostname", "hostname",
            "ipAddress", "ipAddress", "environment.name", "environment.name", "enabled", "enabled");

    public MachineController(MachineService machineService, UserRepository userRepository, PageRequestSupport pageRequestSupport)
    {

        this.machineService = machineService;
        this.userRepository = userRepository;
        this.pageRequestSupport = pageRequestSupport;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MACHINE_VIEW')")
    public String listMachines(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        String normalizedSearch = search == null ? "" : search.trim();
        var spec = pageRequestSupport.create(page, size, sort, direction, "name", MACHINE_SORT_FIELDS);
        Pageable pageable = spec.pageable();
        Page<Machine> machines = machineService.findAll(normalizedSearch, pageable);
        model.addAttribute("machines", machines);
        model.addAttribute("pageSize", spec.pageSize());
        model.addAttribute("sort", spec.sort());
        model.addAttribute("direction", spec.direction());
        model.addAttribute("search", normalizedSearch);
        model.addAttribute("currentPage", "machines");
        return "monitoring/machines";
    }

    @GetMapping("/table")
    @PreAuthorize("hasAuthority('MACHINE_VIEW')")
    public String machineTable(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model)
    {
        String normalizedSearch = search == null ? "" : search.trim();
        var spec = pageRequestSupport.create(page, size, sort, direction, "name", MACHINE_SORT_FIELDS);
        Pageable pageable = spec.pageable();
        Page<Machine> machines = machineService.findAll(normalizedSearch, pageable);
        model.addAttribute("machines", machines);
        model.addAttribute("pageSize", size);
        model.addAttribute("search", normalizedSearch);
        return "monitoring/machines :: machinesTable";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('MACHINE_CONFIG')")
    public String createMachineForm(Model model)
    {
        Machine machine = new Machine();
        machine.setEnabled(true);

        model.addAttribute("machine", machine);
        model.addAttribute("pageTitle", "Add Machine");
        model.addAttribute("submitLabel", "Save Machine");
        model.addAttribute("environments", machineService.findEnabledEnvironments());

        return "monitoring/machine-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MACHINE_CONFIG')")
    public String saveMachine(@ModelAttribute("machine") Machine machine, Authentication authentication)
    {

        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow(
                () -> new IllegalArgumentException("Logged-in user not found: " + authentication.getName()));

        Environment selectedEnvironment = null;

        if (machine.getEnvironment() != null && machine.getEnvironment().getId() != null)
        {

            selectedEnvironment = new Environment();
            selectedEnvironment.setId(machine.getEnvironment().getId());
        }

        if (machine.getId() != null)
        {

            // EDIT existing machine
            Machine existingMachine = machineService.findById(machine.getId());

            existingMachine.setName(machine.getName());
            existingMachine.setHostname(machine.getHostname());
            existingMachine.setIpAddress(machine.getIpAddress());
            existingMachine.setEnvironment(selectedEnvironment);
            existingMachine.setDescription(machine.getDescription());
            existingMachine.setEnabled(machine.isEnabled());

            // Preserve createdBy
            existingMachine.setUpdatedBy(currentUser.getId());

            machineService.save(existingMachine);

        } else
        {

            // CREATE new machine
            machine.setEnvironment(selectedEnvironment);
            machine.setCreatedBy(currentUser.getId());
            machine.setUpdatedBy(currentUser.getId());

            machineService.save(machine);
        }

        return "redirect:/monitoring/machines";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MACHINE_VIEW')")
    public String viewMachine(@PathVariable Long id, Model model)
    {
        Machine machine = machineService.findByIdWithEnvironment(id);
        model.addAttribute("machine", machine);
        return "monitoring/machine-view";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('MACHINE_CONFIG')")
    public String editMachineForm(@PathVariable Long id, Model model)
    {
        Machine machine = machineService.findById(id);

        model.addAttribute("machine", machine);
        model.addAttribute("pageTitle", "Edit Machine");
        model.addAttribute("submitLabel", "Update Machine");
        model.addAttribute("environments", machineService.findEnabledEnvironments());
        return "monitoring/machine-form";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('MACHINE_CONFIG')")
    public String toggleEnabled(@PathVariable Long id)
    {
        machineService.toggleEnabled(id);

        return "redirect:/monitoring/machines";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('MACHINE_CONFIG')")
    public String deleteMachine(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        String machineName = machineService.delete(id);

        redirectAttributes.addFlashAttribute("successMessage",
                "Machine '" + machineName + "' was deleted successfully.");

        return "redirect:/monitoring/machines";
    }

}
