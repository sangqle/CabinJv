package com.cabin.express.profiler.storage;

import com.cabin.express.profiler.ServerProfiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

/**
 * In-memory implementation of metrics storage that keeps recent snapshots
 * in a circular buffer
 */
public class InMemoryStorage implements MetricsStorage {
    private static final int DEFAULT_CAPACITY = 1000;

    private final LinkedBlockingDeque<ServerProfiler.ProfilerSnapshot> buffer;

    public InMemoryStorage() {
        this(DEFAULT_CAPACITY);
    }

    public InMemoryStorage(int capacity) {
        this.buffer = new LinkedBlockingDeque<>(capacity);
    }

    @Override
    public void store(ServerProfiler.ProfilerSnapshot snapshot) {
        // If buffer is full, remove oldest entry
        if (buffer.remainingCapacity() == 0) {
            buffer.pollFirst();
        }

        // Add new snapshot
        buffer.add(snapshot);
    }

    @Override
    public List<ServerProfiler.ProfilerSnapshot> getRecent(int count) {
        return buffer.stream()
                .sorted(Collections.reverseOrder(
                        (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp())
                ))
                .limit(count)
                .collect(Collectors.toList());
    }

    @Override
    public List<ServerProfiler.ProfilerSnapshot> getRange(long fromTimestamp, long toTimestamp) {
        return buffer.stream()
                .filter(s -> s.getTimestamp() >= fromTimestamp && s.getTimestamp() <= toTimestamp)
                .sorted((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                .collect(Collectors.toList());
    }

    @Override
    public void clear() {
        buffer.clear();
    }
}