package com.dxc.monitoring.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.repository.MenuItemRepository;

@Service
public class MenuDefinitionCacheService {
    private final MenuItemRepository repository;

    public MenuDefinitionCacheService(MenuItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "menuDefinitions")
    public List<MenuDefinition> getEnabledMenuDefinitions() {
        return repository.findByEnabledTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(item -> new MenuDefinition(item.getId(), item.getMenuKey(), item.getMenuName(),
                        item.getUrl(), item.getIcon(), item.getParentId(),
                        item.getRequiredPermission(), item.getSectionName()))
                .toList();
    }

    public record MenuDefinition(Long id, String menuKey, String menuName, String url,
            String icon, Long parentId, String requiredPermission, String sectionName) {}
}
