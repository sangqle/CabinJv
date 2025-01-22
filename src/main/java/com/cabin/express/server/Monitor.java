package com.cabin.express.server;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.worker.CabinWorkerPool;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

public class Monitor {

    public static final Monitor Instance = new Monitor();

    private long peakHeapUsed = 0;
    private long peakNonHeapUsed = 0;
    private long peakUsedPhysicalMemorySize = 0;

    public void logResourceUsage(CabinWorkerPool workerPool) {
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

        CabinLogger.info("Resource Usage ----------------------------------------------------------");
        CabinLogger.info("\n" + "+-------------------------- SYSTEM METRICS ---------------------------+\n" + "| Metric                     | Value                                   \n" + "+----------------------------+-----------------------------------------+\n" + String.format("| Process CPU Load           | %.2f%%                                 \n", processCpuLoad) + String.format("| System CPU Load            | %.2f%%                                 \n", systemCpuLoad) + String.format("| Total Physical Memory      | %,d bytes                              \n", totalPhysicalMemorySize) + String.format("| Used Physical Memory       | %,d bytes                              \n", usedPhysicalMemorySize) + String.format("| Free Physical Memory       | %,d bytes                              \n", freePhysicalMemorySize) + "+----------------------------+-----------------------------------------+\n" + "| MEMORY USAGE                                                      \n" + "+----------------------------+-----------------------------------------+\n" + String.format("| Heap Memory Used           | %,d bytes                              \n", heapUsed) + String.format("| Heap Memory Max            | %,d bytes                              \n", heapMax) + String.format("| Non-Heap Memory Used       | %,d bytes                              \n", nonHeapUsed) + String.format("| Peak Heap Memory Used      | %,d bytes                              \n", peakHeapUsed) + String.format("| Peak Non-Heap Memory Used  | %,d bytes                              \n", peakNonHeapUsed) + String.format("| Peak Used Physical Memory  | %,d bytes                              \n", peakUsedPhysicalMemorySize) + "+----------------------------+-----------------------------------------+\n" + "| WORKER POOL METRICS                                                 \n" + "+----------------------------+-----------------------------------------+\n" + String.format("| Worker Pool Size           | %d                                    \n", workerPool.getPoolSize()) + String.format("| Active Threads             | %d                                    \n", workerPool.getActiveThreadCount()) + String.format("| Pending Tasks              | %d                                    \n", workerPool.getPendingTaskCount()) + String.format("| Largest Pool Size          | %d                                    \n", workerPool.getLargestPoolSize()) + "+----------------------------+-----------------------------------------+\n");
    }
}
