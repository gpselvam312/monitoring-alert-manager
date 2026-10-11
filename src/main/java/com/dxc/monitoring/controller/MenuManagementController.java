package com.dxc.monitoring.controller;

import com.dxc.monitoring.entity.MenuItem;
import com.dxc.monitoring.repository.MenuItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/administration/menus")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class MenuManagementController
{
    private static final Pattern MENU_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,79}");
    private static final List<Integer> PAGE_SIZES = List.of(5, 10, 25);
    private static final Map<String, Comparator<MenuItem>> SORTS = Map.of(
            "key", Comparator.comparing(MenuItem::getMenuKey, String.CASE_INSENSITIVE_ORDER),
            "name", Comparator.comparing(MenuItem::getMenuName, String.CASE_INSENSITIVE_ORDER),
            "order", Comparator.comparingInt(MenuItem::getDisplayOrder),
            "enabled", Comparator.comparing(MenuItem::isEnabled));

    private final MenuItemRepository menuItemRepository;

    public MenuManagementController(MenuItemRepository menuItemRepository)
    {
        this.menuItemRepository = menuItemRepository;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "order") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        populateTable(model, page, size, search, sort, direction);
        return "administration/menus";
    }

    @GetMapping("/table")
    public String table(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "order") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            Model model)
    {
        populateTable(model, page, size, search, sort, direction);
        return "administration/menus :: menusTable";
    }

    private void populateTable(Model model, int page, int size, String search, String sort, String direction)
    {
        int safeSize = PAGE_SIZES.contains(size) ? size : 5;
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Comparator<MenuItem> comparator = SORTS.getOrDefault(sort, SORTS.get("order"))
                .thenComparing(MenuItem::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        if ("desc".equalsIgnoreCase(direction))
        {
            comparator = comparator.reversed();
        }

        List<MenuItem> all = menuItemRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder", "id"));
        List<MenuItem> filtered = all.stream()
                .filter(item -> query.isBlank()
                        || item.getMenuKey().toLowerCase(Locale.ROOT).contains(query)
                        || item.getMenuName().toLowerCase(Locale.ROOT).contains(query)
                        || (item.getUrl() != null && item.getUrl().toLowerCase(Locale.ROOT).contains(query))
                        || (item.getRequiredPermission() != null
                            && item.getRequiredPermission().toLowerCase(Locale.ROOT).contains(query)))
                .sorted(comparator)
                .toList();

        int totalPages = (int) Math.ceil((double) filtered.size() / safeSize);
        int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<MenuItem> menuPage = new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());

        Map<Long, String> parentNames = all.stream().collect(Collectors.toMap(
                MenuItem::getId, MenuItem::getMenuName, (left, right) -> left));
        model.addAttribute("menuPage", menuPage);
        model.addAttribute("parentNames", parentNames);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("search", search == null ? "" : search.trim());
        model.addAttribute("currentPage", "menus");
    }

    @GetMapping("/new")
    public String createForm(Model model)
    {
        MenuItem menuItem = new MenuItem();
        menuItem.setEnabled(true);
        menuItem.setDisplayOrder(10);
        model.addAttribute("menuItem", menuItem);
        populateForm(model, null);
        model.addAttribute("pageTitle", "Add Menu");
        model.addAttribute("currentPage", "menus");
        return "administration/menu-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model)
    {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        model.addAttribute("menuItem", menuItem);
        populateForm(model, id);
        model.addAttribute("pageTitle", "Edit Menu");
        model.addAttribute("currentPage", "menus");
        return "administration/menu-form";
    }

    private void populateForm(Model model, Long currentId)
    {
        List<MenuItem> parentMenus = menuItemRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder", "id"))
                .stream()
                .filter(item -> item.getParentId() == null)
                .filter(item -> item.getUrl() == null || item.getUrl().isBlank())
                .filter(item -> currentId == null || !currentId.equals(item.getId()))
                .toList();
        model.addAttribute("parentMenus", parentMenus);
    }

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) Long id,
            @RequestParam String menuKey,
            @RequestParam String menuName,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) Long parentId,
            @RequestParam int displayOrder,
            @RequestParam(required = false) String requiredPermission,
            @RequestParam(defaultValue = "false") boolean enabled,
            Model model,
            RedirectAttributes redirectAttributes)
    {
        String normalizedKey = menuKey == null ? "" : menuKey.trim().toUpperCase(Locale.ROOT);
        String normalizedName = menuName == null ? "" : menuName.trim();
        String normalizedUrl = url == null || url.isBlank() ? null : url.trim();
        String normalizedIcon = icon == null || icon.isBlank() ? null : icon.trim();
        String normalizedPermission = requiredPermission == null || requiredPermission.isBlank()
                ? null : requiredPermission.trim();

        String error = validate(normalizedKey, normalizedName, normalizedUrl, displayOrder, parentId, id);
        if (error != null)
        {
            MenuItem form = new MenuItem();
            form.setId(id);
            form.setMenuKey(normalizedKey);
            form.setMenuName(normalizedName);
            form.setUrl(normalizedUrl);
            form.setIcon(normalizedIcon);
            form.setParentId(parentId);
            form.setDisplayOrder(displayOrder);
            form.setRequiredPermission(normalizedPermission);
            form.setEnabled(enabled);
            model.addAttribute("menuItem", form);
            populateForm(model, id);
            model.addAttribute("pageTitle", id == null ? "Add Menu" : "Edit Menu");
            model.addAttribute("currentPage", "menus");
            model.addAttribute("errorMessage", error);
            return "administration/menu-form";
        }

        MenuItem entity = id == null ? new MenuItem() : menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        entity.setMenuKey(normalizedKey);
        entity.setMenuName(normalizedName);
        entity.setUrl(normalizedUrl);
        entity.setIcon(normalizedIcon);
        entity.setParentId(parentId);
        entity.setDisplayOrder(displayOrder);
        entity.setRequiredPermission(normalizedPermission);
        entity.setEnabled(enabled);
        menuItemRepository.save(entity);

        redirectAttributes.addFlashAttribute("successMessage", "Menu item saved.");
        return "redirect:/administration/menus";
    }

    private String validate(String key, String name, String url, int order, Long parentId, Long id)
    {
        if (!MENU_KEY_PATTERN.matcher(key).matches())
        {
            return "Menu Key must start with a letter and contain only uppercase letters, numbers, and underscores (2–80 characters).";
        }
        if (name.isBlank() || name.length() > 100)
        {
            return "Menu Name is required and must be no longer than 100 characters.";
        }
        if (order < 0)
        {
            return "Display Order must be zero or greater.";
        }
        if (url != null && (!url.startsWith("/") || url.startsWith("//") || url.contains("..")
                || url.contains("\\") || url.contains("\r") || url.contains("\n")))
        {
            return "URL must be a local application path beginning with /; external URLs are not allowed.";
        }
        boolean duplicateKey = menuItemRepository.findAll().stream()
                .anyMatch(item -> key.equals(item.getMenuKey()) && (id == null || !id.equals(item.getId())));
        if (duplicateKey)
        {
            return "That Menu Key is already in use.";
        }
        if (parentId != null)
        {
            MenuItem parent = menuItemRepository.findById(parentId).orElse(null);
            if (parent == null || parent.getParentId() != null || (parent.getUrl() != null && !parent.getUrl().isBlank()))
            {
                return "Choose a top-level grouping menu as the parent.";
            }
            if (id != null && id.equals(parentId))
            {
                return "A menu item cannot be its own parent.";
            }
        }
        return null;
    }

    @PostMapping("/{id}/toggle")
    @Transactional
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes)
    {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found"));
        menuItem.setEnabled(!menuItem.isEnabled());
        menuItemRepository.save(menuItem);
        redirectAttributes.addFlashAttribute("successMessage",
                menuItem.isEnabled() ? "Menu item enabled." : "Menu item disabled.");
        return "redirect:/administration/menus";
    }
}
