package com.cabin.express.worker;

import java.util.concurrent.*;

public class CabinWorkerPool {
    private final ThreadPoolExecutor threadPoolExecutor;


    public CabinWorkerPool(int poolSize, int maxPoolSize) {
        threadPoolExecutor = new ThreadPoolExecutor(poolSize, maxPoolSize, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

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