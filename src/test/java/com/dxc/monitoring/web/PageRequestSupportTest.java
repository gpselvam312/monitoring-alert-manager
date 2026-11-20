package com.dxc.monitoring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageRequestSupportTest {
    @Test
    void appliesPageSizeAndSortWhitelist() {
        PageRequestSupport.PageSpec spec = PageRequestSupport.create(2, 100, "unsafeField", "asc",
                "lastOccurredAt", Map.of("lastOccurredAt", "lastOccurredAt", "severity", "severity"));

        assertEquals(10, spec.pageSize());
        assertEquals(2, spec.pageable().getPageNumber());
        assertEquals("lastOccurredAt", spec.sort());
        assertEquals("asc", spec.direction());
        assertEquals(Sort.Direction.ASC, spec.pageable().getSort().getOrderFor("lastOccurredAt").getDirection());
    }

    @Test
    void supportsAllowedPageSizesAndDescendingDefault() {
        PageRequestSupport.PageSpec spec = PageRequestSupport.create(-1, 25, "severity", "DESC",
                "lastOccurredAt", Map.of("lastOccurredAt", "lastOccurredAt", "severity", "severity"));

        assertEquals(25, spec.pageSize());
        assertEquals(0, spec.pageable().getPageNumber());
        assertEquals("severity", spec.sort());
        assertEquals("desc", spec.direction());
        assertEquals(Sort.Direction.DESC, spec.pageable().getSort().getOrderFor("severity").getDirection());
    }
}
