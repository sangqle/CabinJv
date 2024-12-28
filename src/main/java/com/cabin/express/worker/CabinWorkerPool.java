package com.cabin.express.worker;

import java.rmi.ServerError;
import java.util.LinkedList;
import java.util.Queue;

public class CabinWorkerPool {
    private final Queue<Runnable> taskQueue = new LinkedList<>();
    private final Thread[] workers;

    private boolean isStopped = false;

    public CabinWorkerPool(int poolSize) {
        workers = new Thread[poolSize];
        for (int i = 0; i < poolSize; i++) {
            workers[i] = new Worker();
            workers[i].start();
        }
    }

    public synchronized void submitTask(Runnable task) {
        if (isStopped) {
            throw new IllegalStateException("The pool is stopped");
        }
        taskQueue.add(task);
        notify();
    }

    public synchronized void shutdown() {
        isStopped = true;
        notifyAll();
    }

    private class Worker extends Thread {
        @Override
        public void run() {
            Runnable task;
            while (true) {
                synchronized (CabinWorkerPool.this) {
                    while (taskQueue.isEmpty() && !isStopped) {
                        try {
                            CabinWorkerPool.this.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (isStopped && taskQueue.isEmpty()) {
                        break; // Exit if the pool is stopped and no tasks remain
                    }
                    task = taskQueue.poll(); // Fetch a task from the queue
                }
                try {
                    task.run(); // Execute the task
                } catch (RuntimeException e) {
                    System.err.println(String.format("Error executing task: %s, %s", e, e.getMessage()));
                }
            }
        }
    }
}
