package com.cabin.express.server;

import com.cabin.express.exception.GlobalExceptionHandler;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.router.Router;
import com.cabin.express.worker.CabinWorkerPool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

/**
 * A simple HTTP server using Java NIO.
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */

public class CabinServer {
    private Selector selector;
    private final List<Router> routers = new ArrayList<>();
    private final List<Middleware> globalMiddlewares = new ArrayList<>();
    private final Map<SocketChannel, Long> connectionLastActive = new ConcurrentHashMap<>();

    // Resource logging task
    private ScheduledFuture<?> resourceLoggingTask;
    private ScheduledFuture<?> idleConnectionTask;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Server configuration
    private final int port;
    private final CabinWorkerPool workerPool;
    private final CabinWorkerPool readWorkerPool;
    private final CabinWorkerPool writeWorkerPool;

    private final long connectionTimeoutMillis; // Timeout threshold (30 seconds)
    private final long idleConnectionTimeoutMillis; // Idle connection timeout threshold (60 seconds)

    private volatile boolean isRunning = true; // Flag to control the event loop

    private volatile boolean isLogMetrics = false; // Flag to control the event loop

    /**
     * Creates a new server with the specified port number, default pool size,
     * maximum pool size, and maximum queue capacity.
     *
     * @param port             the port number
     * @param defaultPoolSize  the default number of threads in the thread pool
     * @param maxPoolSize      the maximum number of threads in the thread pool
     * @param maxQueueCapacity the maximum queue capacity
     */
    protected CabinServer(int port, int defaultPoolSize, int maxPoolSize, int maxQueueCapacity, long connectionTimeoutMillis, long idleConnectionTimeoutSeconds, boolean isLogMetrics) {
        this.port = port;
        this.workerPool = new CabinWorkerPool(defaultPoolSize, maxPoolSize, maxQueueCapacity);
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.idleConnectionTimeoutMillis = idleConnectionTimeoutSeconds;

        this.readWorkerPool = new CabinWorkerPool(defaultPoolSize, maxPoolSize, maxQueueCapacity);
        this.writeWorkerPool = new CabinWorkerPool(defaultPoolSize, maxPoolSize, maxQueueCapacity);
        this.isLogMetrics = isLogMetrics;
    }

    /**
     * Start the server
     *
     * @throws IOException if an I/O error occurs
     */
    public void start() throws IOException {
        initializeServer();
        CabinLogger.info("Server started on port " + port);

        // Event loop
        while (isRunning) {
            // Wake up the channels that are ready for I/O operations
            int readyChannels = selector.select(connectionTimeoutMillis);

            if (readyChannels == 0) {
                if (isLogMetrics) {
                    performPeriodicTasks();
                }
                continue;
            }

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                try {
                    if (key.isValid() && key.isAcceptable()) {
                        handleAccept((ServerSocketChannel) key.channel());
                    } else if (key.isValid() && key.isReadable()) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ); // Suspend read events temporarily
                        readWorkerPool.submitTask(() -> handleReadSafely(key), task -> handleBackpressure(key));
                    }
                } catch (Exception e) {
                    CabinLogger.error(String.format("Error handling key: %s", e.getMessage()), e);
                    try {
                        key.channel().close();
                    } catch (IOException ex) {
                        CabinLogger.error("Error closing channel", ex);
                    }
                    key.cancel(); // Cancel the key after handling the event
                }
            }

        }

        // Perform shutdown tasks after exiting the loop
        shutdown();
    }

    private void handleReadSafely(SelectionKey key) {
        try {
            handleRead(key);
        } catch (Exception ex) {
            CabinLogger.error("Error handling read event: " + ex.getMessage(), ex);
            closeChannelSafely(key);
        } finally {
            if (key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_READ); // Re-enable read operations
                key.selector().wakeup(); // Wake up selector to re-register the key
            }
        }
    }

    private void closeChannelSafely(SelectionKey key) {
        if (key == null) {
            CabinLogger.error("Attempted to close a null SelectionKey.", null);
            return;
        }

        SocketChannel channel = (SocketChannel) key.channel();
        try {
            // Cancel the key to deregister the channel from the selector
            if (key.isValid()) {
                key.cancel();
            }

            // Close the channel if it is open
            if (channel != null && channel.isOpen()) {
                CabinLogger.info("Closing channel: " + channel.getRemoteAddress());
                channel.close();
            }
        } catch (IOException e) {
            CabinLogger.error("Error closing channel: " + e.getMessage(), e);
        } catch (Exception e) {
            CabinLogger.error("Unexpected error while closing channel: " + e.getMessage(), e);
        }
    }

    /**
     * Backpressure handling
     */
    private void handleBackpressure(SelectionKey key) {
        CabinLogger.info("Backpressure detected. Suspending read events temporarily.");
        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
    }

    private void performPeriodicTasks() {
        try {
            // Schedule resource logging task if not already scheduled
            if ((resourceLoggingTask == null || resourceLoggingTask.isCancelled() || resourceLoggingTask.isDone()) && !scheduler.isShutdown() && !scheduler.isTerminated()) {
                resourceLoggingTask = scheduler.scheduleAtFixedRate(() -> {
                    Monitor.Instance.logResourceUsage(workerPool);
                }, 0, 30, TimeUnit.SECONDS);
                CabinLogger.info("Resource logging task scheduled.");
            }

            // Schedule idle connection cleanup task if not already scheduled
            if ((idleConnectionTask == null || idleConnectionTask.isCancelled() || idleConnectionTask.isDone()) && !scheduler.isShutdown() && !scheduler.isTerminated()) {
                idleConnectionTask = scheduler.scheduleAtFixedRate(this::closeIdleConnections, 0, idleConnectionTimeoutMillis, TimeUnit.MILLISECONDS);
                CabinLogger.info("Idle connection cleanup task scheduled.");
            }
        } catch (Exception e) {
            CabinLogger.error("Error performing periodic tasks: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize the server by opening a selector and server socket channel.
     *
     * @throws IOException if an I/O error occurs
     */
    private void initializeServer() throws IOException {
        // Open a selector
        selector = Selector.open();

        // Open a server socket channel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress("0.0.0.0", port));
        serverChannel.configureBlocking(false); // Set to non-blocking

        // Register the channel with the selector for accepting connections
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    /**
     * Handles accepting a new incoming connection.
     *
     * @param serverChannel the server socket channel that is accepting the
     *                      connection
     * @throws IOException if an I/O error occurs
     */
    private void handleAccept(ServerSocketChannel serverChannel) throws IOException {
        // Accept the incoming connection
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);

        // Register the new channel for reading
        clientChannel.register(selector, SelectionKey.OP_READ);
    }


    private ByteBuffer getDynamicBuffer(int expectedSize) {
        return ByteBuffer.allocate(Math.max(expectedSize, 1024)); // Minimum size of 1024 bytes
    }

    /**
     * Handles reading data from a client connection.
     *
     * @param key the selection key representing the client connection
     * @throws IOException if an I/O error occurs
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = null;
        try {
            int expectedSize = Math.max(1024, clientChannel.socket().getReceiveBufferSize());
            if (expectedSize > 1024 * 8) {
                buffer = getDynamicBuffer(expectedSize);
            } else {
                buffer = ByteBuffer.allocate(1024);
            }
            buffer.clear();

            if (!clientChannel.isOpen()) {
                CabinLogger.info("Channel is closed: " + clientChannel.getRemoteAddress());
                return;
            }

            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                // Client closed the connection
                CabinLogger.info("Client closed connection: " + clientChannel.getRemoteAddress());
                closeChannelAndCancelKey(clientChannel, key);
                return;
            }
            if (bytesRead > 0) {
                buffer.flip();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                // Enqueue a task to process the request and write the response
                writeWorkerPool.submitTask(() -> handleClientRequest(clientChannel, data));
            }
        } catch (SocketException e) {
            handleSocketException(clientChannel, key, e);
        } catch (IOException e) {
            CabinLogger.error("Error handling read event: " + e.getMessage(), e);
            closeChannelAndCancelKey(clientChannel, key);
        } catch (Throwable e) {
            GlobalExceptionHandler.handleException(e, new Response(clientChannel));
            closeChannelAndCancelKey(clientChannel, key);
        }
    }

    private void handleClientRequest(SocketChannel clientChannel, byte[] data) {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            Request request = new Request(inputStream);
            Response response = new Response(clientChannel);

            boolean handled = false;
            for (Router router : routers) {
                if (router.handleRequest(request, response)) {
                    handled = true;
                    break;
                }
            }

            if (!handled) {
                response.setStatusCode(404);
                response.writeBody("Not Found");
                response.send();
            }

        } catch (IOException e) {
            CabinLogger.error("Error processing client request: " + e.getMessage(), e);
            sendInternalServerError(clientChannel);
        } catch (Throwable e) {
            CabinLogger.error("Error processing client request: " + e.getMessage(), e);
            GlobalExceptionHandler.handleException(e, new Response(clientChannel));
            sendInternalServerError(clientChannel);
        }
    }

    private void handleSocketException(SocketChannel clientChannel, SelectionKey key, SocketException e) {
        if ("Connection reset".equals(e.getMessage())) {
            CabinLogger.info("Connection reset by peer: " + e.getMessage());
        } else {
            CabinLogger.error("Socket exception: " + e.getMessage(), e);
        }
        closeChannelAndCancelKey(clientChannel, key);
    }

    private void sendInternalServerError(SocketChannel clientChannel) {
        try {
            Response response = new Response(clientChannel);
            response.setStatusCode(500);
            response.writeBody("Internal Server Error");
            response.send();
        } catch (Exception e) {
            CabinLogger.error("Error sending internal server error response: " + e.getMessage(), e);
        }
    }

    private void closeChannelAndCancelKey(SocketChannel channel, SelectionKey key) {
        try {
            if (key != null) {
                key.cancel();
            }
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ex) {
            CabinLogger.error("Error closing channel: " + ex.getMessage(), ex);
        }
    }

    /**
     * Add a router to the server
     *
     * @param router
     * @throws IllegalArgumentException
     */
    public void use(Router router) {
        // Validate the router
        if (router == null) {
            throw new IllegalArgumentException("Router cannot be null");
        }
        // Validate the router to ensure not adding the same router multiple times
        if (routers.contains(router)) {
            throw new IllegalArgumentException("Router already added");
        }

        // Validate the router with the same path
        for (Router r : routers) {
            Set<String> endpoint = r.getEndpoint();
            for (String path : endpoint) {
                if (router.getEndpoint().contains(path)) {
                    throw new IllegalArgumentException(String.format("Router with path %s already exists", path));
                }
            }
        }
        for (Middleware middleware : globalMiddlewares) {
            router.use(middleware);
        }
        routers.add(router);
    }

    /**
     * Add a global middleware to all routers
     *
     * @param middleware
     * @return
     * @throws IllegalArgumentException
     */
    public void use(Middleware middleware) {
        globalMiddlewares.add(middleware);
        for (Router router : routers) {
            router.use(middleware);
        }
    }

    private void closeIdleConnections() {
        long now = System.currentTimeMillis();
        for (Map.Entry<SocketChannel, Long> entry : connectionLastActive.entrySet()) {
            SocketChannel channel = entry.getKey();
            long lastActive = entry.getValue();
            if (now - lastActive > connectionTimeoutMillis) {
                try {
                    CabinLogger.info("Closing idle connection: " + channel.getRemoteAddress());
                    if (channel.isOpen()) {
                        channel.close();
                    }
                } catch (IOException e) {
                    CabinLogger.error("Error closing idle connection: " + e.getMessage(), e);
                }
                connectionLastActive.remove(channel); // Remove from map
            }
        }
    }

    private void shutdown() {
        CabinLogger.info("Initiating server shutdown...");

        // Close selector
        if (selector != null && selector.isOpen()) {
            try {
                selector.close();
                CabinLogger.info("Selector closed successfully.");
            } catch (IOException e) {
                CabinLogger.error("Error closing selector: " + e.getMessage(), e);
            }
        }

        // Close all active connections
        connectionLastActive.forEach((channel, lastActive) -> {
            try {
                if (channel != null && channel.isOpen()) {
                    CabinLogger.info("Closed connection: " + channel.getRemoteAddress());
                    channel.close();
                }
            } catch (IOException e) {
                CabinLogger.error("Error closing channel: " + e.getMessage(), e);
            }
        });
        connectionLastActive.clear(); // Ensure the map is cleared after shutdown

        // Shut down worker pool
        try {
            workerPool.shutdown();
            CabinLogger.info("Worker pool shut down successfully.");
        } catch (Exception e) {
            CabinLogger.error("Error shutting down worker pool: " + e.getMessage(), e);
        }

        // Shut down scheduler
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow(); // Force shutdown if tasks don't terminate
                CabinLogger.info("Scheduler forced to shut down.");
            } else {
                CabinLogger.info("Scheduler shut down gracefully.");
            }
        } catch (Exception e) {
            CabinLogger.error("Error shutting down scheduler: " + e.getMessage(), e);
        }

        CabinLogger.info("Server shutdown complete.");
    }


    /**
     * Stop the server
     * <p>
     * This method signals the event loop to exit and shuts down the server.
     * The server will stop accepting new connections and close all active connections.
     * The server will also shut down the worker pool and scheduler.
     * This method is non-blocking and returns immediately.
     */
    public void stop() {
        CabinLogger.info("Stop signal received. Shutting down server...");
        isRunning = false; // Signal the event loop to exit
        if (selector != null) {
            selector.wakeup(); // Wake up the selector to process the change
        }
    }
}
