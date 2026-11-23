package com.dxc.monitoring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageRequestSupportTest {
    private PageRequestSupport support() {
        PaginationProperties properties = new PaginationProperties();
        properties.setAllowedPageSizes(List.of(5, 10, 25));
        properties.setDefaultPageSize(5);
        properties.setMaxPageSize(100);
        return new PageRequestSupport(properties);
    }

    @Test void appliesConfiguredFallbackAndSortWhitelist() {
        var spec = support().create(2, 1000, "unsafeField", "asc",
                "lastOccurredAt", Map.of("lastOccurredAt", "lastOccurredAt", "severity", "severity"));
        assertEquals(5, spec.pageSize());
        assertEquals(2, spec.pageable().getPageNumber());
        assertEquals("lastOccurredAt", spec.sort());
        assertEquals("asc", spec.direction());
        assertEquals(Sort.Direction.ASC, spec.pageable().getSort().getOrderFor("lastOccurredAt").getDirection());
        assertEquals(Sort.Direction.DESC, spec.pageable().getSort().getOrderFor("id").getDirection());
    }

    @Test void supportsAllowedPageSizesAndDescendingDefault() {
        var spec = support().create(-1, 25, "severity", "DESC",
                "lastOccurredAt", Map.of("lastOccurredAt", "lastOccurredAt", "severity", "severity"));
        assertEquals(25, spec.pageSize());
        assertEquals(0, spec.pageable().getPageNumber());
        assertEquals("severity", spec.sort());
        assertEquals("desc", spec.direction());
        assertEquals(Sort.Direction.DESC, spec.pageable().getSort().getOrderFor("severity").getDirection());
    }

    @Test void fixedFetchSizeUsesSameSortWhitelistAndStableTieBreaker() {
        var spec = support().createWithFixedSize(-4, 100, "notAColumn", "asc",
                "lastOccurredAt", Map.of("lastOccurredAt", "lastOccurredAt", "severity", "severity"));
        assertEquals(100, spec.pageSize());
        assertEquals(0, spec.pageable().getPageNumber());
        assertEquals("lastOccurredAt", spec.sort());
        assertEquals("asc", spec.direction());
        assertEquals(Sort.Direction.ASC, spec.pageable().getSort().getOrderFor("lastOccurredAt").getDirection());
        assertEquals(Sort.Direction.DESC, spec.pageable().getSort().getOrderFor("id").getDirection());
    }

    @Test void fixedFetchSizeRejectsInvalidConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> support().createWithFixedSize(0, 0, "severity", "desc",
                        "lastOccurredAt", Map.of("lastOccurredAt", "lastOccurredAt", "severity", "severity")));
    }

    @Test void rejectsInvalidPaginationConfiguration() {
        PaginationProperties properties = new PaginationProperties();
        properties.setDefaultPageSize(7);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PageRequestSupport(properties));
    }
}
