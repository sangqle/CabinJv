package com.cabin.express.profiler.metrics;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

/**
 * Collects and provides overall system metrics
 */
public class SystemMetrics {
    private final OperatingSystemMXBean osMXBean;

    public SystemMetrics() {
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
    }

    /**
     * Collect current system metrics
     */
    public Snapshot collect() {
        String osName = osMXBean.getName();
        String osVersion = osMXBean.getVersion();
        String osArch = osMXBean.getArch();
        int availableProcessors = osMXBean.getAvailableProcessors();

        // Get disk space metrics
        File[] roots = File.listRoots();
        long totalDiskSpace = 0;
        long freeDiskSpace = 0;

        for (File root : roots) {
            totalDiskSpace += root.getTotalSpace();
            freeDiskSpace += root.getFreeSpace();
        }

        // Get JVM uptime
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        // Get physical memory info using reflection if available
        long totalPhysicalMemory = getTotalPhysicalMemory();
        long freePhysicalMemory = getFreePhysicalMemory();
        double memoryUtilization = totalPhysicalMemory > 0
                ? (double)(totalPhysicalMemory - freePhysicalMemory) / totalPhysicalMemory
                : -1.0;

        return new Snapshot(
                osName,
                osVersion,
                osArch,
                availableProcessors,
                totalDiskSpace,
                freeDiskSpace,
                uptimeMs,
                totalPhysicalMemory,
                freePhysicalMemory,
                memoryUtilization
        );
    }

    /**
     * Get total physical memory using reflection
     */
    private long getTotalPhysicalMemory() {
        try {
            Method method = osMXBean.getClass().getMethod("getTotalPhysicalMemorySize");
            method.setAccessible(true);
            return (long) method.invoke(osMXBean);
        } catch (Exception e) {
            return -1; // Not available
        }
    }

    /**
     * Get free physical memory using reflection
     */
    private long getFreePhysicalMemory() {
        try {
            Method method = osMXBean.getClass().getMethod("getFreePhysicalMemorySize");
            method.setAccessible(true);
            return (long) method.invoke(osMXBean);
        } catch (Exception e) {
            return -1; // Not available
        }
    }

    /**
     * Snapshot of system metrics at a point in time
     */
    public static class Snapshot {
        private final String osName;
        private final String osVersion;
        private final String osArch;
        private final int availableProcessors;
        private final long totalDiskSpace;
        private final long freeDiskSpace;
        private final long uptimeMs;
        private final long totalPhysicalMemory;
        private final long freePhysicalMemory;
        private final double memoryUtilization;

        public Snapshot(
                String osName,
                String osVersion,
                String osArch,
                int availableProcessors,
                long totalDiskSpace,
                long freeDiskSpace,
                long uptimeMs,
                long totalPhysicalMemory,
                long freePhysicalMemory,
                double memoryUtilization) {
            this.osName = osName;
            this.osVersion = osVersion;
            this.osArch = osArch;
            this.availableProcessors = availableProcessors;
            this.totalDiskSpace = totalDiskSpace;
            this.freeDiskSpace = freeDiskSpace;
            this.uptimeMs = uptimeMs;
            this.totalPhysicalMemory = totalPhysicalMemory;
            this.freePhysicalMemory = freePhysicalMemory;
            this.memoryUtilization = memoryUtilization;
        }

        // Getters
        public String getOsName() { return osName; }
        public String getOsVersion() { return osVersion; }
        public String getOsArch() { return osArch; }
        public int getAvailableProcessors() { return availableProcessors; }
        public long getTotalDiskSpace() { return totalDiskSpace; }
        public long getFreeDiskSpace() { return freeDiskSpace; }
        public long getUptimeMs() { return uptimeMs; }
        public long getTotalPhysicalMemory() { return totalPhysicalMemory; }
        public long getFreePhysicalMemory() { return freePhysicalMemory; }
        public double getMemoryUtilization() { return memoryUtilization; }

        @Override
        public String toString() {
            return String.format(
                    "System[os=%s %s, arch=%s, uptime=%d min, memory=%.1f%% used]",
                    osName,
                    osVersion,
                    osArch,
                    uptimeMs / (60 * 1000),
                    memoryUtilization * 100
            );
        }
    }
}