package com.cabin.express.profiler.metrics;

import com.cabin.express.logger.CabinLogger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Collects and provides memory-related metrics
 */
public class MemoryMetrics {
    private final MemoryMXBean memoryMXBean;

    // Peak tracking
    private long peakHeapUsed = 0;
    private long peakNonHeapUsed = 0;

    public MemoryMetrics() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * Collect current memory metrics
     */
    public Snapshot collect() {
        try {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

            // Update peak values
            peakHeapUsed = Math.max(peakHeapUsed, heapUsage.getUsed());
            peakNonHeapUsed = Math.max(peakNonHeapUsed, nonHeapUsage.getUsed());

            // Calculate derived metrics - handle case where max might be undefined (-1)
            long heapMax = heapUsage.getMax();
            double heapUtilization = (heapMax > 0) ?
                    (double) heapUsage.getUsed() / heapMax : 0.0;

            return new Snapshot(
                    heapUsage.getInit(),
                    heapUsage.getUsed(),
                    heapUsage.getCommitted(),
                    heapMax,
                    nonHeapUsage.getUsed(),
                    peakHeapUsed,
                    peakNonHeapUsed,
                    heapUtilization
            );
        } catch (Exception e) {
            CabinLogger.error("Error collecting memory metrics", e);
            // Return a default snapshot with zeros to avoid NPEs
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0.0);
        }
    }

    /**
     * Snapshot of memory metrics at a point in time
     */
    public static class Snapshot {
        private final long heapInit;
        private final long heapUsed;
        private final long heapCommitted;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long peakHeapUsed;
        private final long peakNonHeapUsed;
        private final double heapUtilization;

        public Snapshot(
                long heapInit,
                long heapUsed,
                long heapCommitted,
                long heapMax,
                long nonHeapUsed,
                long peakHeapUsed,
                long peakNonHeapUsed,
                double heapUtilization) {
            this.heapInit = heapInit;
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.peakHeapUsed = peakHeapUsed;
            this.peakNonHeapUsed = peakNonHeapUsed;
            this.heapUtilization = heapUtilization;
        }

        // Getters
        public long getHeapInit() { return heapInit; }
        public long getHeapUsed() { return heapUsed; }
        public long getHeapCommitted() { return heapCommitted; }
        public long getHeapMax() { return heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getPeakHeapUsed() { return peakHeapUsed; }
        public long getPeakNonHeapUsed() { return peakNonHeapUsed; }
        public double getHeapUtilization() { return heapUtilization; }

        @Override
        public String toString() {
            return String.format(
                    "Memory[heap=%dMB (%.1f%% of max), nonHeap=%dMB, peak=%dMB]",
                    heapUsed / (1024 * 1024),
                    heapUtilization * 100,
                    nonHeapUsed / (1024 * 1024),
                    peakHeapUsed / (1024 * 1024)
            );
        }
    }
}