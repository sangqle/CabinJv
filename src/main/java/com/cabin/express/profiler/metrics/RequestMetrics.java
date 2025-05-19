package com.cabin.express.profiler.metrics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Collects and provides HTTP request-related metrics
 */
public class RequestMetrics {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    // Track request counts and times by path
    private final Map<String, PathStats> pathStats = new ConcurrentHashMap<>();

    // Track request counts by status code
    private final Map<Integer, AtomicLong> statusCounts = new ConcurrentHashMap<>();

    // Track response time metrics
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);

    /**
     * Record metrics for a completed request
     */
    public void recordRequest(String path, int statusCode, long durationNanos) {
        totalRequests.incrementAndGet();

        if (statusCode >= 200 && statusCode < 400) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }

        // Update status code counts
        statusCounts.computeIfAbsent(statusCode, k -> new AtomicLong(0))
                .incrementAndGet();

        // Convert duration to milliseconds
        long durationMs = durationNanos / 1_000_000;
        totalResponseTimeMs.addAndGet(durationMs);

        // Update path-specific stats
        PathStats stats = pathStats.computeIfAbsent(path, k -> new PathStats());
        stats.recordRequest(durationMs, statusCode);
    }

    /**
     * Get a snapshot of current metrics
     */
    public Snapshot getSnapshot() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();

        double avgResponseTimeMs = total > 0
                ? (double) totalResponseTimeMs.get() / total
                : 0.0;

        Map<Integer, Long> statusCodeMap = new HashMap<>();
        statusCounts.forEach((code, count) -> statusCodeMap.put(code, count.get()));

        List<PathStat> topPaths = getTopPaths(10);

        return new Snapshot(
                total,
                successful,
                failed,
                avgResponseTimeMs,
                statusCodeMap,
                topPaths
        );
    }

    /**
     * Get top N paths by request count
     */
    public List<PathStat> getTopPaths(int n) {
        return pathStats.entrySet().stream()
                .sorted(Map.Entry.<String, PathStats>comparingByValue()
                        .reversed())
                .limit(n)
                .map(entry -> new PathStat(
                        entry.getKey(),
                        entry.getValue().getCount(),
                        entry.getValue().getAverageResponseTimeMs(),
                        entry.getValue().getErrorCount()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Inner class to track per-path statistics
     */
    private static class PathStats implements Comparable<PathStats> {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);

        public void recordRequest(long durationMs, int statusCode) {
            count.incrementAndGet();
            totalTimeMs.addAndGet(durationMs);

            if (statusCode >= 400) {
                errorCount.incrementAndGet();
            }
        }

        public long getCount() {
            return count.get();
        }

        public double getAverageResponseTimeMs() {
            long c = count.get();
            return c > 0 ? (double) totalTimeMs.get() / c : 0.0;
        }

        public long getErrorCount() {
            return errorCount.get();
        }

        @Override
        public int compareTo(PathStats other) {
            return Long.compare(this.count.get(), other.count.get());
        }
    }

    /**
     * Snapshot of request metrics at a point in time
     */
    public static class Snapshot {
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;
        private final double averageResponseTimeMs;
        private final Map<Integer, Long> statusCounts;
        private final List<PathStat> topPaths;

        public Snapshot(
                long totalRequests,
                long successfulRequests,
                long failedRequests,
                double averageResponseTimeMs,
                Map<Integer, Long> statusCounts,
                List<PathStat> topPaths) {
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.failedRequests = failedRequests;
            this.averageResponseTimeMs = averageResponseTimeMs;
            this.statusCounts = statusCounts;
            this.topPaths = topPaths;
        }

        // Getters
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public long getFailedRequests() { return failedRequests; }
        public double getAverageResponseTimeMs() { return averageResponseTimeMs; }
        public Map<Integer, Long> getStatusCounts() { return statusCounts; }
        public List<PathStat> getTopPaths(int i) { return topPaths; }

        @Override
        public String toString() {
            return String.format(
                    "Requests[total=%d, success=%d, failed=%d, avg=%.2fms]",
                    totalRequests,
                    successfulRequests,
                    failedRequests,
                    averageResponseTimeMs
            );
        }
    }

    /**
     * Path-specific metrics for reporting
     */
    public static class PathStat {
        private final String path;
        private final long count;
        private final double avgResponseTimeMs;
        private final long errorCount;

        public PathStat(String path, long count, double avgResponseTimeMs, long errorCount) {
            this.path = path;
            this.count = count;
            this.avgResponseTimeMs = avgResponseTimeMs;
            this.errorCount = errorCount;
        }

        // Getters
        public String getPath() { return path; }
        public long getCount() { return count; }
        public double getAvgResponseTimeMs() { return avgResponseTimeMs; }
        public long getErrorCount() { return errorCount; }
    }
}