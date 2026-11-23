package com.dxc.monitoring.service;

/**
 * Keeps menu administration validation and sidebar rendering on the same URL policy.
 * Menu links must be local, path-only application routes.
 */
public final class MenuUrlPolicy {
    private MenuUrlPolicy() {}

    public static boolean isSafeLocalPath(String url) {
        if (url == null || url.isBlank()) return false;
        String path = url.trim();
        return path.startsWith("/") && !path.startsWith("//")
                && !path.contains("..") && !path.contains("\\")
                && !path.contains("\r") && !path.contains("\n")
                && !path.contains("%") && !path.contains("?") && !path.contains("#");
    }
}
