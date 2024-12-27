package com.cabin.express.server;

public class ServerBuilder {
    private int port = 8080; // Default port
    private int threadPoolSize = 10; // Default thread pool size

    public ServerBuilder setPort(int port) {
        if(port < 1 || port > 65535) {
            return this;
        }
        this.port = port;
        return this;
    }

    public ServerBuilder setThreadPoolSize(int threadPoolSize) {
        if (threadPoolSize < 1) {
            return this;
        }
        if (threadPoolSize > 100) {
            threadPoolSize = 100;
        }
        this.threadPoolSize = threadPoolSize;
        return this;
    }

    public CabinServer build() {
        return new CabinServer(port, threadPoolSize);
    }
}
