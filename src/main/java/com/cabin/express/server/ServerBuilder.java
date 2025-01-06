package com.cabin.express.server;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

/**
 * A builder class for creating a CabinServer instance
 * Author: Sang Le
 * Created: 2024-12-24
 * <p>
 * Example usage:
 * <pre>
 *     CabinServer server = new ServerBuilder()
 *     .setPort(8080)
 *     .setMaxPoolSize(20)
 *     .build();
 *     server.start();
 */

public class ServerBuilder {
    private int port = 8080; // Default port
    private int maxPoolSize = 20; // Default thread pool size

    /**
     * Set the port number
     *
     * @param port the port number
     * @return the server builder
     */
    public ServerBuilder setPort(int port) {
        if (port < 1 || port > 65535) {
            return this;
        }
        this.port = port;
        return this;
    }

    /**
     * Set the maximum number of threads in the thread pool
     *
     * @param maxPoolSize the maximum number of threads
     * @return the server builder
     */
    public ServerBuilder setMaxPoolSize(int maxPoolSize) {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        int availableProcessors = osBean.getAvailableProcessors();
        long totalPhysicalMemorySize = osBean.getTotalPhysicalMemorySize();

        // Example logic to set max pool size based on CPU and memory
        int poolSizeBasedOnCpu = availableProcessors * 2; // 2 threads per CPU core
        int poolSizeBasedOnMemory = (int) (totalPhysicalMemorySize / (1024 * 1024 * 512)); // 1 thread per 512MB of RAM

        // Set the max pool size to the minimum of the two values
        int maxSystemPoolSize = Math.min(poolSizeBasedOnCpu, poolSizeBasedOnMemory);

        if (maxPoolSize < 1) {
            return this;
        }
        if (maxPoolSize > maxSystemPoolSize) {
            maxPoolSize = maxSystemPoolSize;
        }

        this.maxPoolSize = maxPoolSize;
        return this;
    }

    public CabinServer build() {
        return new CabinServer(port, 96);
    }
}
