package com.cabin.express.server;

import com.cabin.express.exception.GlobalExceptionHandler;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.middleware.MiddlewareChain;
import com.cabin.express.router.Router;
import com.cabin.express.worker.CabinWorkerPool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple HTTP server using Java NIO.
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */

public class CabinServer {
    /**
     * The selector for handling multiple channels.
     */
    private Selector selector;
    private final List<Router> routers = new ArrayList<>();
    private final List<Middleware> globalMiddlewares = new ArrayList<>();
    private final Map<SocketChannel, Long> connectionLastActive = new ConcurrentHashMap<>();
    private final Map<SocketChannel, ByteArrayOutputStream> clientBuffers = new HashMap<>();

    // Resource logging task
    private ScheduledFuture<?> resourceLoggingTask;
    private ScheduledFuture<?> idleConnectionTask;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Server configuration
    private final int port;
    /**
     * Worker pools for read and write operations
     * The read worker pool is used for handling incoming requests,
     * while the writing worker pool is used for sending responses.
     */
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
    protected CabinServer(
            int port,
            int defaultPoolSize,
            int maxPoolSize,
            int maxQueueCapacity,
            long connectionTimeoutMillis,
            long idleConnectionTimeoutSeconds,
            boolean isLogMetrics
    ) {
        this.port = port;
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
                        readWorkerPool.submitTask(
                                () -> handleReadSafely(key), task -> handleBackpressure(key)
                        );
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
        } catch (ClosedChannelException ex) {
            CabinLogger.info("Channel is closed: " + ex.getMessage());
            key.cancel();
        } catch (SocketTimeoutException ex) {
            CabinLogger.info("Socket timeout: " + ex.getMessage());
        } catch (IOException ex) {
            CabinLogger.error("Error handling read event: " + ex.getMessage(), ex);
            closeChannelSafely(key);
        } catch (Exception ex) {
            CabinLogger.error("Error handling read event: " + ex.getMessage(), ex);
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
            Map<String, CabinWorkerPool> workerPools = new HashMap<>();
            workerPools.put("WriteCabinWorkerPool", writeWorkerPool);
            workerPools.put("ReadCabinWorkerPool", readWorkerPool);

            // Schedule resource logging task if not already scheduled
            if ((resourceLoggingTask == null || resourceLoggingTask.isCancelled() || resourceLoggingTask.isDone())
                    && !scheduler.isShutdown()
                    && !scheduler.isTerminated()
            ) {
                resourceLoggingTask = scheduler.scheduleAtFixedRate(() -> {
                    Monitor.Instance.logResourceUsage(workerPools);
                }, 0, 30, TimeUnit.SECONDS);
                CabinLogger.info("Resource logging task scheduled.");
            }

            // Schedule idle connection cleanup task if not already scheduled
            if ((idleConnectionTask == null || idleConnectionTask.isCancelled() || idleConnectionTask.isDone())
                    && !scheduler.isShutdown()
                    && !scheduler.isTerminated()
            ) {
                idleConnectionTask = scheduler.scheduleAtFixedRate(
                        this::closeIdleConnections,
                        0,
                        idleConnectionTimeoutMillis,
                        TimeUnit.MILLISECONDS
                );
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
        ByteBuffer buffer = ByteBuffer.allocate(8192); // Reuse buffer size

        try {
            if (!clientChannel.isOpen()) {
                CabinLogger.info("Channel is closed: " + clientChannel.getRemoteAddress());
                return;
            }

            // Persistent storage for request data
            ByteArrayOutputStream requestBuffer = clientBuffers.computeIfAbsent(
                    clientChannel,
                    k -> new ByteArrayOutputStream()
            );
            int bytesRead;

            // Read data in chunks and accumulate
            while ((bytesRead = clientChannel.read(buffer)) > 0) {
                buffer.flip();
                byte[] tempData = new byte[buffer.remaining()];
                buffer.get(tempData);
                requestBuffer.write(tempData);
                buffer.clear();
            }

            // If client disconnects, check if full request is received
            if (bytesRead == -1) {
                CabinLogger.info("Client closed connection: " + clientChannel.getRemoteAddress());
                closeChannelAndCancelKey(clientChannel, key);
                clientBuffers.remove(clientChannel);
                return;
            }

            // Ensure we have received a full HTTP request before processing
            if (isRequestComplete(requestBuffer.toByteArray())) {
                writeWorkerPool.submitTask(() -> {
                    handleClientRequest(clientChannel, requestBuffer);
                    clientBuffers.remove(clientChannel);
                });
            } else {
                // Wait for more data before processing
                CabinLogger.info("Waiting for more data from: " + clientChannel.getRemoteAddress());
            }
        } catch (Throwable e) {
            CabinLogger.error("Error reading from client: " + e.getMessage(), e);
            closeChannelAndCancelKey(clientChannel, key);
            clientBuffers.remove(clientChannel);
        }
    }


    private void handleClientRequest(SocketChannel clientChannel, ByteArrayOutputStream byteArrayOutputStream) {
        try {
            Request request = new Request(byteArrayOutputStream);
            Response response = new Response(clientChannel);

            // Create final handler that handles case when no route matches
            Handler finalHandler = (req, res) -> {
                res.setStatusCode(404);
                res.writeBody("Not Found");
                res.send();
            };

            // Create combined middleware list
            List<Middleware> allMiddleware = new ArrayList<>(globalMiddlewares);

            // Add routers as middleware
            allMiddleware.addAll(routers);

            // Create and execute the middleware chain
            MiddlewareChain chain = new MiddlewareChain(allMiddleware, finalHandler);
            chain.next(request, response);

        } catch (IOException e) {
            CabinLogger.error("Error processing client request: " + e.getMessage(), e);
            sendInternalServerError(clientChannel);
        } catch (Throwable e) {
            CabinLogger.error("Error processing client request: " + e.getMessage(), e);
            GlobalExceptionHandler.handleException(e, new Response(clientChannel));
            sendInternalServerError(clientChannel);
        }
    }

    private boolean isRequestComplete(byte[] requestData) {
        String requestString = new String(requestData, StandardCharsets.ISO_8859_1);

        // **Check if request has full headers**
        if (!requestString.contains("\r\n\r\n")) {
            return false; // Headers are incomplete
        }

        // **Check if Content-Length is fully received**
        Matcher contentLengthMatcher = Pattern.compile("Content-Length: (\\d+)").matcher(requestString);
        if (contentLengthMatcher.find()) {
            int contentLength = Integer.parseInt(contentLengthMatcher.group(1));
            int headerEndIndex = requestString.indexOf("\r\n\r\n") + 4;
            return requestData.length >= headerEndIndex + contentLength;
        }

        // **Check if request is chunked (Transfer-Encoding: chunked)**
        if (requestString.contains("Transfer-Encoding: chunked")) {
            return requestString.endsWith("0\r\n\r\n");
        }

        return true; // If no Content-Length or chunked, assume complete
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
     * @param router the router to add
     * @throws IllegalArgumentException if router is null or already added
     */
    public void use(Router router) {
        // Validate the router
        if (router == null) {
            throw new IllegalArgumentException("Router cannot be null");
        }

        // Check for duplicate router
        if (routers.contains(router)) {
            throw new IllegalArgumentException("Router already added");
        }

        // Check for conflicting routes
        for (Router existingRouter : routers) {
            Set<String> existingEndpoints = existingRouter.getEndpoint();
            Set<String> newEndpoints = router.getEndpoint();

            for (String newPath : newEndpoints) {
                if (existingEndpoints.contains(newPath)) {
                    throw new IllegalArgumentException(
                            String.format("Conflicting route found: %s", newPath));
                }
            }
        }

        // Apply global middleware to the router
        for (Middleware middleware : globalMiddlewares) {
            router.use(middleware);
        }

        routers.add(router);
    }

    /**
     * Add a global middleware to all routers
     *
     * @param middleware the middleware to add
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
            readWorkerPool.shutdown();
            writeWorkerPool.shutdown();
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
