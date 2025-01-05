package com.cabin.express.worker;

import java.util.concurrent.*;

public class CabinWorkerPool {
    private final ThreadPoolExecutor threadPoolExecutor;


    public CabinWorkerPool(int poolSize, int maxPoolSize) {
        int corePoolSize = Math.max(1, poolSize); // Ensure at least one thread
        int maximumPoolSize = Math.max(corePoolSize, maxPoolSize); // Ensure max pool size is not less than core pool size
        int queueCapacity = 1000; // Example capacity, adjust as needed

        threadPoolExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy() // Example policy, adjust as needed
        );

    }

    public void submitTask(Runnable task) {
        if (threadPoolExecutor.isShutdown()) {
            throw new IllegalStateException("The pool is stopped");
        }
        threadPoolExecutor.submit(task);
    }

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
}