package com.cabin.express.server;

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
        if (maxPoolSize < 1) {
            return this;
        }
        if (maxPoolSize > 100) {
            maxPoolSize = 100;
        }
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    public CabinServer build() {
        return new CabinServer(port, maxPoolSize);
    }
}
