package com.cabin.express.worker;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A worker pool for executing tasks in a multi-threaded environment.
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */
public class CabinWorkerPool {
    private final ThreadPoolExecutor threadPoolExecutor;

    /*
     * Creates a new worker pool with the specified pool size, maximum pool size, and queue capacity.
     * @param poolSize the number of threads to keep in the pool, even if they are idle
     * @param maxPoolSize the maximum number of threads to allow in the pool
     * @param queueCapacity the queue capacity
     * @throws IllegalArgumentException if the pool size is less than 1, the maximum pool size is less than the pool size, or the queue capacity is negative
     *
     */
    public CabinWorkerPool(int poolSize, int maxPoolSize, int queueCapacity) {
        int corePoolSize = Math.max(1, poolSize);
        int maximumPoolSize = Math.max(corePoolSize, maxPoolSize);
        int maximumQueueCapacity = Math.max(0, queueCapacity);
        threadPoolExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(maximumQueueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Submits a task for execution.
     *
     * @param task the task to execute
     * @throws IllegalStateException if the pool is stopped
     */
    public void submitTask(Runnable task, Consumer<Runnable> onBackpressure) {
        if (threadPoolExecutor.isShutdown()) {
            throw new IllegalStateException("The pool is stopped");
        }

        if (threadPoolExecutor.getQueue().remainingCapacity() == 0) {
            if (onBackpressure != null) {
                onBackpressure.accept(task);
            } else {
                throw new RejectedExecutionException("The queue is full");
            }
        } else {
            threadPoolExecutor.submit(task);
        }
    }

    public void submitTask(Runnable task) {
        if (threadPoolExecutor.isShutdown()) {
            throw new IllegalStateException("The pool is stopped");
        }
        threadPoolExecutor.submit(task);
    }

    /**
     * Shuts down the worker pool.
     */
    public void shutdown() {
        threadPoolExecutor.shutdown();
        try {
            if (!threadPoolExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPoolExecutor.shutdownNow();
                if (!threadPoolExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Executor service did not terminate");
                }
            }
        } catch (InterruptedException e) {
            threadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the number of active threads in the pool.
     *
     * @return the number of active threads
     */
    public int getActiveThreadCount() {
        return threadPoolExecutor.getActiveCount();
    }

    /**
     * Returns the number of pending tasks in the queue.
     *
     * @return the number of pending tasks
     */
    public int getPendingTaskCount() {
        return threadPoolExecutor.getQueue().size();
    }

    /**
     * Returns the number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        return threadPoolExecutor.getPoolSize();
    }

    /**
     * Returns the maximum number of threads in the pool.
     *
     * @return the maximum number of threads
     */
    public int getLargestPoolSize() {
        return threadPoolExecutor.getLargestPoolSize();
    }
}