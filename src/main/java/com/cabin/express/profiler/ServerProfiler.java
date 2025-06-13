package com.cabin.express.profiler;

import com.cabin.express.logger.CabinLogger;
import com.cabin.express.profiler.metrics.*;
import com.cabin.express.profiler.reporting.LogReporter;
import com.cabin.express.profiler.reporting.MetricsReporter;
import com.cabin.express.profiler.storage.InMemoryStorage;
import com.cabin.express.profiler.storage.MetricsStorage;
import com.cabin.express.worker.CabinWorkerPool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ServerProfiler provides comprehensive server performance monitoring and profiling.
 * It collects metrics about CPU, memory, threads, and request handling, and can report
 * these metrics through various channels.
 */
public class ServerProfiler {
    // Singleton instance
    public static final ServerProfiler INSTANCE = new ServerProfiler();

    private final MemoryMetrics memoryMetrics;
    private final CPUMetrics cpuMetrics;
    private final ThreadMetrics threadMetrics;
    private final RequestMetrics requestMetrics;
    private final SystemMetrics systemMetrics;

    private final List<MetricsReporter> reporters = new ArrayList<>();
    private final MetricsStorage storage;

    private ScheduledExecutorService scheduler;
    private Duration samplingInterval = Duration.ofSeconds(10);
    private boolean isRunning = false;

    // Thread-local storage for request timing
    private static final ThreadLocal<Long> requestStartTime = new ThreadLocal<>();

    // Request latency tracking
    private final Map<String, List<Long>> pathLatencies = new ConcurrentHashMap<>();

    private boolean enabled = false; // Default to disabled

    private ServerProfiler() {
        this.memoryMetrics = new MemoryMetrics();
        this.cpuMetrics = new CPUMetrics();
        this.threadMetrics = new ThreadMetrics();
        this.requestMetrics = new RequestMetrics();
        this.systemMetrics = new SystemMetrics();

        // Default to in-memory storage
        this.storage = new InMemoryStorage();

        // Add default console reporter
        this.reporters.add(new LogReporter());
    }

    /**
     * Set whether profiling is enabled
     * @param enabled true to enable profiling, false to disable
     * @return this instance for method chaining
     */
    public ServerProfiler setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && !isRunning) {
            // Auto-start if enabled and not running
            start();
        } else if (!enabled && isRunning) {
            // Auto-stop if disabled and running
            stop();
        }
        return this;
    }

    /**
     * Check if profiling is enabled
     * @return true if profiling is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Start profiling with the configured settings
     */
    public void start() {
        if (isRunning || !enabled) {
            return; // Don't start if already running or not enabled
        }

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
                this::collectAndReportMetrics,
                0,
                samplingInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        isRunning = true;
        CabinLogger.info("ServerProfiler started with sampling interval of "
                + samplingInterval.getSeconds() + " seconds");
    }

    /**
     * Stop the profiling process
     */
    public void stop() {
        if (!isRunning || scheduler == null) {
            return;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        isRunning = false;
        CabinLogger.info("ServerProfiler stopped");
    }

    /**
     * Set the sampling interval for metrics collection
     */
    public ServerProfiler withSamplingInterval(Duration interval) {
        this.samplingInterval = interval;
        return this;
    }

    /**
     * Add a custom metrics reporter
     */
    public ServerProfiler addReporter(MetricsReporter reporter) {
        this.reporters.add(reporter);
        return this;
    }

    /**
     * Mark the start of request processing for latency tracking
     */
    public void startRequest() {
        if (!enabled) return;
        requestStartTime.set(System.nanoTime());
    }

    /**
     * Mark the end of request processing and record metrics
     *
     * @param path The request path for path-specific metrics
     * @param statusCode The HTTP status code
     */
    public void endRequest(String path, int statusCode) {
        if (!enabled) return;
        Long start = requestStartTime.get();
        if (start == null) {
            return;
        }

        long duration = System.nanoTime() - start;
        requestMetrics.recordRequest(path, statusCode, duration);

        // Store path-specific latency
        pathLatencies.computeIfAbsent(path, k -> new ArrayList<>())
                .add(duration / 1_000_000); // Convert to ms

        // Clean up thread local
        requestStartTime.remove();
    }

    /**
     * Collect all metrics and send to reporters and storage
     */
    private void collectAndReportMetrics() {
        try {
            ProfilerSnapshot snapshot = new ProfilerSnapshot();

            // Collect all metrics
            snapshot.setMemoryMetrics(memoryMetrics.collect());
            snapshot.setCpuMetrics(cpuMetrics.collect());
            snapshot.setThreadMetrics(threadMetrics.collect());
            snapshot.setRequestMetrics(requestMetrics.getSnapshot());
            snapshot.setSystemMetrics(systemMetrics.collect());

            // Store the snapshot
            storage.store(snapshot);

            // Report to all reporters
            for (MetricsReporter reporter : reporters) {
                reporter.report(snapshot);
            }
        } catch (Exception e) {
            CabinLogger.error("Error collecting server metrics", e);
        }
    }

    /**
     * Collect worker pool metrics for all worker pools
     */
    public void collectWorkerPoolMetrics(Map<String, CabinWorkerPool> workerPools) {
        threadMetrics.collectWorkerPoolMetrics(workerPools);
    }

    /**
     * Get a snapshot of current metrics
     */
    public ProfilerSnapshot getSnapshot() {
        ProfilerSnapshot snapshot = new ProfilerSnapshot();
        snapshot.setMemoryMetrics(memoryMetrics.collect());
        snapshot.setCpuMetrics(cpuMetrics.collect());
        snapshot.setThreadMetrics(threadMetrics.collect());
        snapshot.setRequestMetrics(requestMetrics.getSnapshot());
        snapshot.setSystemMetrics(systemMetrics.collect());
        return snapshot;
    }

    /**
     * Get latency statistics for a specific path
     */
    public Map<String, Object> getPathLatencyStats(String path) {
        List<Long> latencies = pathLatencies.get(path);
        if (latencies == null || latencies.isEmpty()) {
            return Map.of("path", path, "count", 0);
        }

        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;

        for (Long latency : latencies) {
            sum += latency;
            min = Math.min(min, latency);
            max = Math.max(max, latency);
        }

        double avg = (double) sum / latencies.size();

        // Get p95 (95th percentile)
        latencies.sort(Long::compare);
        int p95Index = (int) Math.ceil(0.95 * latencies.size()) - 1;
        long p95 = latencies.get(p95Index);

        return Map.of(
                "path", path,
                "count", latencies.size(),
                "avgMs", avg,
                "minMs", min,
                "maxMs", max,
                "p95Ms", p95
        );
    }

    /**
     * Class representing a complete snapshot of all metrics at a point in time
     */
    public static class ProfilerSnapshot {
        private MemoryMetrics.Snapshot memoryMetrics;
        private CPUMetrics.Snapshot cpuMetrics;
        private ThreadMetrics.Snapshot threadMetrics;
        private RequestMetrics.Snapshot requestMetrics;
        private SystemMetrics.Snapshot systemMetrics;
        private long timestamp = System.currentTimeMillis();

        // Getters and setters
        public MemoryMetrics.Snapshot getMemoryMetrics() { return memoryMetrics; }
        public void setMemoryMetrics(MemoryMetrics.Snapshot memoryMetrics) { this.memoryMetrics = memoryMetrics; }

        public CPUMetrics.Snapshot getCpuMetrics() { return cpuMetrics; }
        public void setCpuMetrics(CPUMetrics.Snapshot cpuMetrics) { this.cpuMetrics = cpuMetrics; }

        public ThreadMetrics.Snapshot getThreadMetrics() { return threadMetrics; }
        public void setThreadMetrics(ThreadMetrics.Snapshot threadMetrics) { this.threadMetrics = threadMetrics; }

        public RequestMetrics.Snapshot getRequestMetrics() { return requestMetrics; }
        public void setRequestMetrics(RequestMetrics.Snapshot requestMetrics) { this.requestMetrics = requestMetrics; }

        public SystemMetrics.Snapshot getSystemMetrics() { return systemMetrics; }
        public void setSystemMetrics(SystemMetrics.Snapshot systemMetrics) { this.systemMetrics = systemMetrics; }

        public long getTimestamp() { return timestamp; }
    }
}