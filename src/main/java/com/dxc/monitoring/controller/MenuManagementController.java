package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.MenuItem;
import com.dxc.monitoring.repository.MenuItemRepository;
import com.dxc.monitoring.web.PageRequestSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/administration/menus")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class MenuManagementController {
    private static final Pattern MENU_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,79}");
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "key", "menuKey", "name", "menuName", "order", "displayOrder", "enabled", "enabled");
    private final MenuItemRepository menuItemRepository;
    private final PageRequestSupport pageRequestSupport;

    public MenuManagementController(MenuItemRepository menuItemRepository, PageRequestSupport pageRequestSupport) {
        this.menuItemRepository = menuItemRepository;
        this.pageRequestSupport = pageRequestSupport;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "order") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        populateTable(model, page, size, search, sort, direction);
        return "administration/menus";
    }

    @GetMapping("/table")
    public String table(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "order") String sort,
            @RequestParam(defaultValue = "asc") String direction, Model model) {
        populateTable(model, page, size, search, sort, direction);
        return "administration/menus :: menusTable";
    }

    private void populateTable(Model model, int page, int size, String search, String sort, String direction) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        var spec = pageRequestSupport.create(page, size, sort, direction, "order", SORT_FIELDS);
        Page<MenuItem> menuPage = menuItemRepository.search(query, spec.pageable());
        Set<Long> parentIds = menuPage.getContent().stream().map(MenuItem::getParentId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> parentNames = menuItemRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, MenuItem::getMenuName));
        model.addAttribute("menuPage", menuPage);
        model.addAttribute("parentNames", parentNames);
        model.addAttribute("pageSize", spec.pageSize());
        model.addAttribute("search", query);
        model.addAttribute("sort", spec.sort());
        model.addAttribute("direction", spec.direction());
        model.addAttribute("currentPage", "menu-management");
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        MenuItem menuItem = new MenuItem();
        menuItem.setEnabled(true);
        menuItem.setDisplayOrder(10);
        model.addAttribute("menuItem", menuItem);
        populateForm(model, null);
        model.addAttribute("pageTitle", "Add Menu");
        model.addAttribute("currentPage", "menu-management");
        return "administration/menu-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        model.addAttribute("menuItem", menuItem);
        populateForm(model, id);
        model.addAttribute("pageTitle", "Edit Menu");
        model.addAttribute("currentPage", "menu-management");
        return "administration/menu-form";
    }

    private void populateForm(Model model, Long currentId) {
        List<MenuItem> parentMenus = menuItemRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder", "id"))
                .stream().filter(item -> item.getParentId() == null)
                .filter(item -> item.getUrl() == null || item.getUrl().isBlank())
                .filter(item -> currentId == null || !currentId.equals(item.getId())).toList();
        model.addAttribute("parentMenus", parentMenus);
    }

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) Long id, @RequestParam String menuKey,
            @RequestParam String menuName, @RequestParam(required = false) String url,
            @RequestParam(required = false) String icon, @RequestParam(required = false) Long parentId,
            @RequestParam int displayOrder, @RequestParam(required = false) String requiredPermission,
            @RequestParam(defaultValue = "false") boolean enabled, Model model,
            RedirectAttributes redirectAttributes) {
        String normalizedKey = menuKey == null ? "" : menuKey.trim().toUpperCase(Locale.ROOT);
        String normalizedName = menuName == null ? "" : menuName.trim();
        String normalizedUrl = url == null || url.isBlank() ? null : url.trim();
        String normalizedIcon = icon == null || icon.isBlank() ? null : icon.trim();
        String normalizedPermission = requiredPermission == null || requiredPermission.isBlank()
                ? null : requiredPermission.trim();
        String error = validate(normalizedKey, normalizedName, normalizedUrl, displayOrder, parentId, id);
        if (error != null) {
            MenuItem form = new MenuItem();
            form.setId(id); form.setMenuKey(normalizedKey); form.setMenuName(normalizedName);
            form.setUrl(normalizedUrl); form.setIcon(normalizedIcon); form.setParentId(parentId);
            form.setDisplayOrder(displayOrder); form.setRequiredPermission(normalizedPermission);
            form.setEnabled(enabled);
            model.addAttribute("menuItem", form);
            populateForm(model, id);
            model.addAttribute("pageTitle", id == null ? "Add Menu" : "Edit Menu");
            model.addAttribute("currentPage", "menu-management");
            model.addAttribute("errorMessage", error);
            return "administration/menu-form";
        }
        MenuItem entity = id == null ? new MenuItem() : menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        entity.setMenuKey(normalizedKey); entity.setMenuName(normalizedName); entity.setUrl(normalizedUrl);
        entity.setIcon(normalizedIcon); entity.setParentId(parentId); entity.setDisplayOrder(displayOrder);
        entity.setRequiredPermission(normalizedPermission); entity.setEnabled(enabled);
        menuItemRepository.save(entity);
        redirectAttributes.addFlashAttribute("successMessage", "Menu item saved.");
        return "redirect:/administration/menus";
    }

    private String validate(String key, String name, String url, int order, Long parentId, Long id) {
        if (!MENU_KEY_PATTERN.matcher(key).matches())
            return "Menu Key must start with a letter and contain only uppercase letters, numbers, and underscores (2–80 characters).";
        if (name.isBlank() || name.length() > 100)
            return "Menu Name is required and must be no longer than 100 characters.";
        if (order < 0) return "Display Order must be zero or greater.";
        if (url != null && (!url.startsWith("/") || url.startsWith("//") || url.contains("..")
                || url.contains("\\") || url.contains("\r") || url.contains("\n")
                || url.matches("(?i).*%(2f|5c|2e).*")))
            return "URL must be a safe local application path beginning with /; external or encoded traversal paths are not allowed.";
        boolean duplicate = id == null ? menuItemRepository.existsByMenuKeyIgnoreCase(key)
                : menuItemRepository.existsByMenuKeyIgnoreCaseAndIdNot(key, id);
        if (duplicate) return "That Menu Key is already in use.";
        if (parentId != null) {
            MenuItem parent = menuItemRepository.findById(parentId).orElse(null);
            if (parent == null || parent.getParentId() != null || (parent.getUrl() != null && !parent.getUrl().isBlank()))
                return "Choose a top-level grouping menu as the parent.";
            if (id != null && id.equals(parentId)) return "A menu item cannot be its own parent.";
        }
        return null;
    }

    @PostMapping("/{id}/toggle")
    @Transactional
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        menuItem.setEnabled(!menuItem.isEnabled());
        menuItemRepository.save(menuItem);
        redirectAttributes.addFlashAttribute("successMessage",
                menuItem.isEnabled() ? "Menu item enabled." : "Menu item disabled.");
        return "redirect:/administration/menus";
    }
}
