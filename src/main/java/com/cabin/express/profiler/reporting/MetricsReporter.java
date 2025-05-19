package com.cabin.express.profiler.reporting;

import com.cabin.express.profiler.ServerProfiler;

/**
 * Interface for metrics reporting implementations
 */
public interface MetricsReporter {
    /**
     * Report metrics from the provided snapshot
     */
    void report(ServerProfiler.ProfilerSnapshot snapshot);
}