package com.dxc.monitoring.web;

import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Shared server-side list paging and sort whitelist.
 * Controllers provide only the sort fields that are safe for their query.
 */
public final class PageRequestSupport {
    private static final Set<Integer> PAGE_SIZES = Set.of(5, 10, 25);

    private PageRequestSupport() {}

    public static PageSpec create(int page, int size, String sort, String direction,
            String defaultSort, Map<String, String> allowedSorts) {
        int safePage = Math.max(0, page);
        int safeSize = PAGE_SIZES.contains(size) ? size : 10;
        String sortKey = sort != null && allowedSorts.containsKey(sort) ? sort : defaultSort;
        String sortProperty = allowedSorts.getOrDefault(sortKey, defaultSort);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(sortDirection, sortProperty).and(Sort.by(Sort.Direction.DESC, "id")));
        return new PageSpec(pageable, safeSize, sortKey, sortDirection.name().toLowerCase());
    }

    public record PageSpec(Pageable pageable, int pageSize, String sort, String direction) {}
}
