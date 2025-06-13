package com.cabin.express.server;

import com.cabin.express.logger.CabinLogger;
import com.cabin.express.worker.CabinWorkerPool;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;

public class Monitor {

    public static final Monitor Instance = new Monitor();

    private long peakHeapUsed = 0;
    private long peakNonHeapUsed = 0;
    private long peakUsedPhysicalMemorySize = 0;

    public void logResourceUsage(Map<String, CabinWorkerPool> workerPools) {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();

        double processCpuLoad = osBean.getProcessCpuLoad() * 100;
        double systemCpuLoad = osBean.getSystemCpuLoad() * 100;
        long totalPhysicalMemorySize = osBean.getTotalPhysicalMemorySize();
        long freePhysicalMemorySize = osBean.getFreePhysicalMemorySize();
        long usedPhysicalMemorySize = totalPhysicalMemorySize - freePhysicalMemorySize;

        long heapUsed = heapMemoryUsage.getUsed();
        long heapMax = heapMemoryUsage.getMax();
        long nonHeapUsed = nonHeapMemoryUsage.getUsed();

        peakHeapUsed = Math.max(peakHeapUsed, heapUsed);
        peakNonHeapUsed = Math.max(peakNonHeapUsed, nonHeapUsed);
        peakUsedPhysicalMemorySize = Math.max(peakUsedPhysicalMemorySize, usedPhysicalMemorySize);

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("Resource Usage ----------------------------------------------------------\n");
        logBuilder.append("+-------------------------- SYSTEM METRICS ---------------------------+\n");
        logBuilder.append(String.format("| Process CPU Load           | %.2f%%                                 \n", processCpuLoad));
        logBuilder.append(String.format("| System CPU Load            | %.2f%%                                 \n", systemCpuLoad));
        logBuilder.append(String.format("| Total Physical Memory      | %,d bytes                              \n", totalPhysicalMemorySize));
        logBuilder.append(String.format("| Used Physical Memory       | %,d bytes                              \n", usedPhysicalMemorySize));
        logBuilder.append(String.format("| Free Physical Memory       | %,d bytes                              \n", freePhysicalMemorySize));
        logBuilder.append("+----------------------------+-----------------------------------------+\n");
        logBuilder.append("| MEMORY USAGE                                                      \n");
        logBuilder.append("+----------------------------+-----------------------------------------+\n");
        logBuilder.append(String.format("| Heap Memory Used           | %,d bytes                              \n", heapUsed));
        logBuilder.append(String.format("| Heap Memory Max            | %,d bytes                              \n", heapMax));
        logBuilder.append(String.format("| Non-Heap Memory Used       | %,d bytes                              \n", nonHeapUsed));
        logBuilder.append(String.format("| Peak Heap Memory Used      | %,d bytes                              \n", peakHeapUsed));
        logBuilder.append(String.format("| Peak Non-Heap Memory Used  | %,d bytes                              \n", peakNonHeapUsed));
        logBuilder.append(String.format("| Peak Used Physical Memory  | %,d bytes                              \n", peakUsedPhysicalMemorySize));
        logBuilder.append("+----------------------------+-----------------------------------------+\n");

        // Log each worker pool separately
        logBuilder.append("| WORKER POOL METRICS                                                 \n");
        logBuilder.append("+----------------------------+-----------------------------------------+\n");

        for (Map.Entry<String, CabinWorkerPool> entry : workerPools.entrySet()) {
            String poolName = entry.getKey();
            CabinWorkerPool pool = entry.getValue();

            logBuilder.append(String.format("| Worker Pool Name           | %s                                    \n", poolName));
            logBuilder.append(String.format("| Worker Pool Size           | %d                                    \n", pool.getPoolSize()));
            logBuilder.append(String.format("| Active Threads             | %d                                    \n", pool.getActiveThreadCount()));
            logBuilder.append(String.format("| Pending Tasks              | %d                                    \n", pool.getPendingTaskCount()));
            logBuilder.append(String.format("| Largest Pool Size          | %d                                    \n", pool.getLargestPoolSize()));
            logBuilder.append("+----------------------------+-----------------------------------------+\n");
        }

        CabinLogger.info(logBuilder.toString());
    }
}
