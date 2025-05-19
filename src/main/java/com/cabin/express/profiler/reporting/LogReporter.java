package com.cabin.express.profiler.reporting;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.profiler.ServerProfiler;

/**
 * Reporter that logs metrics to the CabinLogger
 */
public class LogReporter implements MetricsReporter {
    private long lastReportTime = 0;
    private final long reportIntervalMs;

    public LogReporter() {
        this(60000); // Default to report every minute
    }

    public LogReporter(long reportIntervalMs) {
        this.reportIntervalMs = reportIntervalMs;
    }

    @Override
    public void report(ServerProfiler.ProfilerSnapshot snapshot) {
        long now = System.currentTimeMillis();

        // Only log at the configured interval
        if (now - lastReportTime < reportIntervalMs) {
            return;
        }

        // Update last report time
        lastReportTime = now;

        // Log summary information
        CabinLogger.info("=== SERVER METRICS REPORT ===");
        CabinLogger.info(snapshot.getMemoryMetrics().toString());
        CabinLogger.info(snapshot.getCpuMetrics().toString());
        CabinLogger.info(snapshot.getThreadMetrics().toString());
        CabinLogger.info(snapshot.getRequestMetrics().toString());
        CabinLogger.info(snapshot.getSystemMetrics().toString());
        CabinLogger.info("=============================");
    }
}