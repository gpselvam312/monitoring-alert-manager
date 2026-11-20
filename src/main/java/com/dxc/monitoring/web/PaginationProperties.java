package com.dxc.monitoring.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "monitoring.pagination")
public class PaginationProperties {
    private int defaultPageSize = 5;
    private int maxPageSize = 100;
    private List<Integer> allowedPageSizes = new ArrayList<>(List.of(5, 10, 25));

    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int value) { defaultPageSize = value; }
    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int value) { maxPageSize = value; }
    public List<Integer> getAllowedPageSizes() { return allowedPageSizes; }
    public void setAllowedPageSizes(List<Integer> values) {
        allowedPageSizes = values == null ? new ArrayList<>() : values.stream().distinct().sorted().toList();
    }
    public void validate() {
        if (maxPageSize < 1 || allowedPageSizes.isEmpty()
                || allowedPageSizes.stream().anyMatch(size -> size == null || size < 1 || size > maxPageSize)
                || !allowedPageSizes.contains(defaultPageSize))
            throw new IllegalArgumentException("Pagination configuration must have a valid default, allowed sizes, and maximum page size.");
    }
}
