package com.cabin.express.profiler.storage;

import com.cabin.express.profiler.ServerProfiler;

import java.util.List;

/**
 * Interface for storing profiler metrics
 */
public interface MetricsStorage {
    /**
     * Store a profiler snapshot
     */
    void store(ServerProfiler.ProfilerSnapshot snapshot);

    /**
     * Retrieve recent snapshots, limited to the specified count
     */
    List<ServerProfiler.ProfilerSnapshot> getRecent(int count);

    /**
     * Retrieve snapshots between specified timestamps
     */
    List<ServerProfiler.ProfilerSnapshot> getRange(long fromTimestamp, long toTimestamp);

    /**
     * Clear all stored metrics data
     */
    void clear();
}