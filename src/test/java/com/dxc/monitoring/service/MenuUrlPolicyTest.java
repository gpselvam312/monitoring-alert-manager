package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuUrlPolicyTest {
    @Test
    void acceptsLocalApplicationPaths() {
        assertTrue(MenuUrlPolicy.isSafeLocalPath("/dashboard"));
        assertTrue(MenuUrlPolicy.isSafeLocalPath("/administration/users/12/edit"));
    }

    @Test
    void rejectsExternalEncodedOrNonPathUrls() {
        assertFalse(MenuUrlPolicy.isSafeLocalPath("https://example.com"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath("//example.com/path"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath("/%2e%2e/admin"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath("/%2f%2fevil"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath("/dashboard?next=/admin"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath("/dashboard#section"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath("/admin\\users"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath("dashboard"));
        assertFalse(MenuUrlPolicy.isSafeLocalPath(" "));
    }
}
