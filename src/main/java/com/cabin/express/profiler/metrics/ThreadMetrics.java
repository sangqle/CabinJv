package com.cabin.express.profiler.metrics;

import com.cabin.express.worker.CabinWorkerPool;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects and provides thread-related metrics
 */
public class ThreadMetrics {
    private final ThreadMXBean threadMXBean;
    private Map<String, WorkerPoolMetrics> workerPoolMetrics = new HashMap<>();

    public ThreadMetrics() {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    /**
     * Collect current thread metrics
     */
    public Snapshot collect() {
        int threadCount = threadMXBean.getThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();
        int daemonThreadCount = threadMXBean.getDaemonThreadCount();
        long totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();

        return new Snapshot(
                threadCount,
                peakThreadCount,
                daemonThreadCount,
                totalStartedThreadCount,
                new HashMap<>(workerPoolMetrics)
        );
    }

    /**
     * Collect metrics from worker pools
     */
    public void collectWorkerPoolMetrics(Map<String, CabinWorkerPool> pools) {
        workerPoolMetrics.clear();

        for (Map.Entry<String, CabinWorkerPool> entry : pools.entrySet()) {
            CabinWorkerPool pool = entry.getValue();

            WorkerPoolMetrics metrics = new WorkerPoolMetrics(
                    pool.getPoolSize(),
                    pool.getActiveThreads(),
                    pool.getQueueSize(),
                    pool.getCompletedTasks()
            );

            workerPoolMetrics.put(entry.getKey(), metrics);
        }
    }

    /**
     * Snapshot of thread metrics at a point in time
     */
    public static class Snapshot {
        private final int threadCount;
        private final int peakThreadCount;
        private final int daemonThreadCount;
        private final long totalStartedThreadCount;
        private final Map<String, WorkerPoolMetrics> workerPoolMetrics;

        public Snapshot(
                int threadCount,
                int peakThreadCount,
                int daemonThreadCount,
                long totalStartedThreadCount,
                Map<String, WorkerPoolMetrics> workerPoolMetrics) {
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.daemonThreadCount = daemonThreadCount;
            this.totalStartedThreadCount = totalStartedThreadCount;
            this.workerPoolMetrics = workerPoolMetrics;
        }

        // Getters
        public int getThreadCount() { return threadCount; }
        public int getPeakThreadCount() { return peakThreadCount; }
        public int getDaemonThreadCount() { return daemonThreadCount; }
        public long getTotalStartedThreadCount() { return totalStartedThreadCount; }
        public Map<String, WorkerPoolMetrics> getWorkerPoolMetrics() { return workerPoolMetrics; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "Threads[count=%d, peak=%d, daemon=%d, total=%d]",
                    threadCount,
                    peakThreadCount,
                    daemonThreadCount,
                    totalStartedThreadCount
            ));

            if (!workerPoolMetrics.isEmpty()) {
                sb.append(", WorkerPools[");
                boolean first = true;
                for (Map.Entry<String, WorkerPoolMetrics> entry : workerPoolMetrics.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
                sb.append("]");
            }

            return sb.toString();
        }
    }

    /**
     * Metrics specific to a worker pool
     */
    public static class WorkerPoolMetrics {
        private final int poolSize;
        private final int activeThreads;
        private final int queueSize;
        private final long completedTasks;

        public WorkerPoolMetrics(int poolSize, int activeThreads, int queueSize, long completedTasks) {
            this.poolSize = poolSize;
            this.activeThreads = activeThreads;
            this.queueSize = queueSize;
            this.completedTasks = completedTasks;
        }

        // Getters
        public int getPoolSize() { return poolSize; }
        public int getActiveThreads() { return activeThreads; }
        public int getQueueSize() { return queueSize; }
        public long getCompletedTasks() { return completedTasks; }

        @Override
        public String toString() {
            return String.format("size=%d, active=%d, queued=%d", poolSize, activeThreads, queueSize);
        }
    }
}