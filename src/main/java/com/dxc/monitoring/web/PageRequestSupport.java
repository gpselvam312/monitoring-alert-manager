package com.dxc.monitoring.web;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Component
public class PageRequestSupport {
    private final PaginationProperties properties;

    public PageRequestSupport(PaginationProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    public int defaultPageSize() { return properties.getDefaultPageSize(); }

    public PageSpec create(int page, int size, String sort, String direction,
            String defaultSort, Map<String, String> allowedSorts) {
        int safePage = Math.max(0, page);
        int safeSize = properties.getAllowedPageSizes().contains(size) && size <= properties.getMaxPageSize()
                ? size : properties.getDefaultPageSize();
        String sortKey = sort != null && allowedSorts.containsKey(sort) ? sort : defaultSort;
        String sortProperty = allowedSorts.getOrDefault(sortKey, defaultSort);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort stableSort = Sort.by(sortDirection, sortProperty);
        if (!"id".equals(sortProperty)) stableSort = stableSort.and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(safePage, safeSize, stableSort);
        return new PageSpec(pageable, safeSize, sortKey, sortDirection.name().toLowerCase());
    }

    public record PageSpec(Pageable pageable, int pageSize, String sort, String direction) {}
}
