package com.cabin.express.utils;

public class PathUtils {
    public static String combinePaths(String basePath, String subPath) {
        String base = (basePath == null || basePath.isEmpty()) ? "/" : basePath;
        String sub = (subPath == null) ? "" : subPath;

        // Normalize base path (remove trailing slash unless root)
        if (base.endsWith("/") && base.length() > 1) {
            base = base.substring(0, base.length() - 1);
        }
        // Normalize sub path (remove leading slash)
        if (sub.startsWith("/")) {
            sub = sub.substring(1);
        }

        String fullPath = base + (sub.isEmpty() ? "" : "/" + sub);

        // Replace multiple slashes with a single slash
        fullPath = fullPath.replaceAll("/+", "/");

        // Ensure leading slash
        if (!fullPath.startsWith("/")) {
            fullPath = "/" + fullPath;
        }
        // Remove trailing slash unless root
        if (fullPath.endsWith("/") && fullPath.length() > 1) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }
        return fullPath;
    }
}