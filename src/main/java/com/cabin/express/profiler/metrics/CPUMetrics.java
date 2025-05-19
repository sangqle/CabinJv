package com.cabin.express.profiler.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

/**
 * Collects and provides CPU-related metrics
 */
public class CPUMetrics {
    private final OperatingSystemMXBean osMXBean;
    private double lastProcessCpuLoad = 0.0;
    private double lastSystemCpuLoad = 0.0;
    private double peakCpuLoad = 0.0;

    public CPUMetrics() {
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
    }

    /**
     * Collect current CPU metrics
     */
    public Snapshot collect() {
        double processCpuLoad = getProcessCpuLoad();
        double systemCpuLoad = getSystemCpuLoad();
        int availableProcessors = osMXBean.getAvailableProcessors();
        double systemLoadAverage = osMXBean.getSystemLoadAverage();

        // Update last and peak values
        lastProcessCpuLoad = processCpuLoad;
        lastSystemCpuLoad = systemCpuLoad;
        peakCpuLoad = Math.max(peakCpuLoad, processCpuLoad);

        return new Snapshot(
                processCpuLoad,
                systemCpuLoad,
                availableProcessors,
                systemLoadAverage,
                peakCpuLoad
        );
    }

    /**
     * Get process CPU load using reflection on the OperatingSystemMXBean
     * This is a workaround because these methods are available in
     * com.sun.management.OperatingSystemMXBean but not in the public interface
     */
    private double getProcessCpuLoad() {
        try {
            Method method = osMXBean.getClass().getMethod("getProcessCpuLoad");
            method.setAccessible(true);
            return (double) method.invoke(osMXBean);
        } catch (Exception e) {
            return -1.0; // Return -1 to indicate unavailable
        }
    }

    /**
     * Get system CPU load using reflection
     */
    private double getSystemCpuLoad() {
        try {
            // Try getCpuLoad first (JDK 14+)
            try {
                Method method = osMXBean.getClass().getMethod("getCpuLoad");
                method.setAccessible(true);
                return (double) method.invoke(osMXBean);
            } catch (NoSuchMethodException nsme) {
                // Fall back to getSystemCpuLoad for older JDKs
                Method method = osMXBean.getClass().getMethod("getSystemCpuLoad");
                method.setAccessible(true);
                return (double) method.invoke(osMXBean);
            }
        } catch (Exception e) {
            return -1.0; // Return -1 to indicate unavailable
        }
    }

    /**
     * Snapshot of CPU metrics at a point in time
     */
    public static class Snapshot {
        private final double processCpuLoad;
        private final double systemCpuLoad;
        private final int availableProcessors;
        private final double systemLoadAverage;
        private final double peakCpuLoad;

        public Snapshot(
                double processCpuLoad,
                double systemCpuLoad,
                int availableProcessors,
                double systemLoadAverage,
                double peakCpuLoad) {
            this.processCpuLoad = processCpuLoad;
            this.systemCpuLoad = systemCpuLoad;
            this.availableProcessors = availableProcessors;
            this.systemLoadAverage = systemLoadAverage;
            this.peakCpuLoad = peakCpuLoad;
        }

        // Getters
        public double getProcessCpuLoad() { return processCpuLoad; }
        public double getSystemCpuLoad() { return systemCpuLoad; }
        public int getAvailableProcessors() { return availableProcessors; }
        public double getSystemLoadAverage() { return systemLoadAverage; }
        public double getPeakCpuLoad() { return peakCpuLoad; }

        @Override
        public String toString() {
            return String.format(
                    "CPU[process=%.1f%%, system=%.1f%%, processors=%d, load=%.2f]",
                    processCpuLoad * 100,
                    systemCpuLoad * 100,
                    availableProcessors,
                    systemLoadAverage
            );
        }
    }
}