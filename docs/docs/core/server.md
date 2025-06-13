---
id: server
title: Server 
---

# CabinServer

CabinServer is the core component of the CabinJ framework, providing a high-performance, non-blocking HTTP server built on Java NIO. It follows a boss/worker thread architecture with configurable thread pools to efficiently handle concurrent connections.

## Architecture Overview

The CabinServer handles incoming HTTP connections using a multi-threaded architecture:

- A **boss thread** accepts incoming connections and distributes them to worker threads
- Multiple **worker threads** handle reading and processing HTTP requests
- Dedicated **worker pools** execute request processing and response writing tasks
- Built-in **connection tracking** manages timeouts for idle connections
- Support for **middleware chains** and **routing** to organize request handlers

## Server Configuration

CabinServer is designed to be highly configurable to meet different performance requirements. You can customize all aspects of the server using the `ServerBuilder` class, which provides a fluent interface for configuration.

### Basic Configuration

```java
CabinServer server = new ServerBuilder()
    .setPort(8080)
    .setDefaultPoolSize(20)
    .setMaxPoolSize(100)
    .build();
```

### Advanced Configuration

```java
CabinServer server = new ServerBuilder()
    .setPort(3000)
    .setDefaultPoolSize(50)
    .setMaxPoolSize(200)
    .setMaxQueueCapacity(5000)
    .setTimeout(5000)           // Connection timeout in ms
    .setIdleTimeoutMiles(60000) // Idle timeout in ms
    .enableRequestLogging(true)
    .enableProfiler(true)
    .enableProfilerDashboard(true)
    .setProfilerSamplingInterval(Duration.ofSeconds(15))
    .build();
```

## Performance Tuning

The server architecture is designed for high throughput and low latency. You can tune several parameters to optimize for your specific workload:

### Thread Pool Configuration

```java
CabinServer server = new ServerBuilder()
    .setDefaultPoolSize(Runtime.getRuntime().availableProcessors() * 2)
    .setMaxPoolSize(500)
    .setMaxQueueCapacity(10000)
    .build();
```

### Connection Management

```java
CabinServer server = new ServerBuilder()
    // Close idle connections after 2 minutes
    .setIdleTimeoutMiles(120000)
    // Connection establishment timeout (2 seconds)
    .setTimeout(2000)
    .build();
```

## Logging and Monitoring

CabinServer provides built-in logging and performance monitoring capabilities:

### Configuring Logging

```java
CabinServer server = new ServerBuilder()
    .setLogDirectory("/var/log/cabin")
    .setLogLevel(LoggerConfig.LogLevel.INFO)
    .enableRequestLogging(true)
    .configureLogger(config -> {
        config.setConsoleOutput(true);
        config.setLogPattern("[%d{yyyy-MM-dd HH:mm:ss}] [%p] %m%n");
    })
    .build();
```

### Performance Profiling

```java
CabinServer server = new ServerBuilder()
    .enableProfiler(true)
    .enableProfilerDashboard(true)
    .setProfilerSamplingInterval(Duration.ofSeconds(10))
    .build();
```

When the profiler dashboard is enabled, you can access detailed metrics and performance graphs at `/profiler/dashboard`.

## Lifecycle Management

### Starting the Server

You can start the server synchronously:

```java
CabinServer server = new ServerBuilder().build();
server.start();
```

Or asynchronously with a lifecycle callback:

```java
server.start(new ServerLifecycleCallback() {
    @Override
    public void onServerInitialized(int port) {
        System.out.println("Server started on port " + port);
    }
    
    @Override
    public void onServerStopped() {
        System.out.println("Server stopped");
    }
    
    @Override
    public void onServerFailed(Exception e) {
        System.err.println("Server failed to start: " + e.getMessage());
    }
});
```

### Stopping the Server

```java
// Stop with default timeout (5 seconds)
server.stop();

// Stop with custom timeout (10 seconds)
server.stop(10000);
```