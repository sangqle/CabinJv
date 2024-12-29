package com.cabin.express.server;

public class ServerBuilder {
    private int port = 8080; // Default port
    private int maxPoolSize = 20; // Default thread pool size

    public ServerBuilder setPort(int port) {
        if(port < 1 || port > 65535) {
            return this;
        }
        this.port = port;
        return this;
    }

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
