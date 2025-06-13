package com.cabin.express.server;

import com.cabin.express.exception.GlobalExceptionHandler;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.interfaces.ServerLifecycleCallback;
import com.cabin.express.logger.CabinLogger;
import com.cabin.express.middleware.MiddlewareChain;
import com.cabin.express.profiler.ServerProfiler;
import com.cabin.express.profiler.reporting.DashboardReporter;
import com.cabin.express.router.Router;
import com.cabin.express.worker.CabinWorkerPool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CabinServer {
    // --- Core fields for boss/worker selectors ---
    private Selector bossSelector;
    private final Selector[] workerSelectors;
    private final Thread bossThread;
    private final Thread[] workerThreads;
    private int nextWorkerIndex = 0; // for round-robin
    private final ByteBufferPool bufferPool;

    // --- Middleware stack ---
    private final List<Middleware> middlewareStack = new ArrayList<>();

    // --- Connection tracking for timeouts ---
    private final Map<SocketChannel, Long> connectionLastActive = new ConcurrentHashMap<>();

    // --- Worker pools for business logic ---
    private final CabinWorkerPool readWorkerPool;
    private final CabinWorkerPool writeWorkerPool;

    // --- Server config ---
    private final int port;
    private final long idleConnectionTimeoutMillis;

    // --- Profiler flags ---
    private volatile boolean profilerEnabled = false;
    private volatile boolean profilerDashboardEnabled = false;

    // --- Lifecycle flags ---
    private volatile boolean isRunning = false;
    private volatile boolean isStopped = false;

    // Constructor
    protected CabinServer(
            int port,
            int defaultPoolSize,
            int maxPoolSize,
            int maxQueueCapacity,
            long idleConnectionTimeoutSeconds,
            boolean profilerEnabled,
            boolean profilerDashboardEnabled
    ) {
        this.port = port;
        this.idleConnectionTimeoutMillis = idleConnectionTimeoutSeconds * 1000L;
        this.readWorkerPool = new CabinWorkerPool(defaultPoolSize, maxPoolSize, maxQueueCapacity);
        this.writeWorkerPool = new CabinWorkerPool(defaultPoolSize, maxPoolSize, maxQueueCapacity);

        this.profilerEnabled = profilerEnabled;
        this.profilerDashboardEnabled = profilerDashboardEnabled;

        // 1. Initialize bossSelector & workerSelectors
        int workerCount = Runtime.getRuntime().availableProcessors();
        this.workerSelectors = new Selector[workerCount];
        this.workerThreads = new Thread[workerCount];

        // 2. Create placeholder threads; actual logic set in initializeServer()
        this.bossThread = new Thread(this::runBossLoop, "CabinServer-BossThread");
        for (int i = 0; i < workerCount; i++) {
            int idx = i;
            this.workerThreads[i] = new Thread(() -> runWorkerLoop(idx), "CabinServer-Worker-" + idx);
        }

        // 3. Initialize ByteBufferPool
        this.bufferPool = new ByteBufferPool(8192, 100, 10);
    }

    // Set profiler flags
    public void setProfilerEnabled(boolean enabled) {
        this.profilerEnabled = enabled;
        ServerProfiler.INSTANCE.setEnabled(enabled);
    }

    public boolean isProfilerEnabled() {
        return profilerEnabled;
    }

    public void setProfilerDashboardEnabled(boolean enabled) {
        this.profilerDashboardEnabled = enabled;
    }

    public boolean isProfilerDashboardEnabled() {
        return profilerDashboardEnabled;
    }

    /**
     * Starts the server synchronously
     **/
    public boolean start() throws IOException {
        try {
            isStopped = false;
            isRunning = true;
            initializeServer();              // opens selectors & server channel
            CabinLogger.info("Server started on port " + port);

            if (profilerEnabled) {
                ServerProfiler.INSTANCE.setEnabled(true);
                if (profilerDashboardEnabled) {
                    setupDashboard();
                }
            }

            // 1. Start all worker threads (they each run runWorkerLoop)
            for (Thread wt : workerThreads) {
                wt.start();
            }
            // 2. Start boss thread (it runs runBossLoop)
            bossThread.start();

            // give a small pause to ensure threads are up
            Thread.sleep(100);
            return isRunning;
        } catch (Exception e) {
            CabinLogger.error("Failed to start server", e);
            isRunning = false;
            return false;
        }
    }

    /**
     * Starts the server asynchronously with a lifecycle callback
     **/
    public void start(ServerLifecycleCallback callback) {
        new Thread(() -> {
            try {
                isStopped = false;
                isRunning = true;
                initializeServer();
                if (callback != null) {
                    callback.onServerInitialized(port);
                }

                if (profilerEnabled) {
                    ServerProfiler.INSTANCE.setEnabled(true);
                    if (profilerDashboardEnabled) {
                        setupDashboard();
                    }
                }

                // Start worker threads
                for (Thread wt : workerThreads) {
                    wt.start();
                }
                // Start boss thread
                bossThread.start();

                // Wait for boss thread to exit (indicates shutdown)
                bossThread.join();
                if (callback != null) {
                    callback.onServerStopped();
                }
            } catch (Exception e) {
                isRunning = false;
                isStopped = true;
                CabinLogger.error("Error starting server: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onServerFailed(e);
                }
                try {
                    shutdown();
                } catch (Exception ignored) {
                }
            }
        }, "CabinServer-Main").start();
    }

    /**
     * Core initialization: open selectors and bind server socket
     **/
    private void initializeServer() throws IOException {
        // 1. Open boss selector
        bossSelector = Selector.open();

        // 2. Open worker selectors
        for (int i = 0; i < workerSelectors.length; i++) {
            workerSelectors[i] = Selector.open();
        }

        // 3. Create nonblocking ServerSocketChannel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress("0.0.0.0", port));
        serverChannel.configureBlocking(false);

        // 4. Register serverChannel with bossSelector for OP_ACCEPT
        serverChannel.register(bossSelector, SelectionKey.OP_ACCEPT);
    }

    /**
     * The “boss” loop only handles OP_ACCEPT and dispatches to worker selectors
     **/
    private void runBossLoop() {
        try {
            while (isRunning) {
                // 1. Block until at least one accept‐ready event or timeout
                bossSelector.select(500);

                Set<SelectionKey> keys = bossSelector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel clientChannel = serverChannel.accept();
                        if (clientChannel != null) {
                            clientChannel.configureBlocking(false);
                            // Round‐robin assignment to a worker selector
                            int workerIdx = nextWorkerIndex;
                            nextWorkerIndex = (nextWorkerIndex + 1) % workerSelectors.length;

                            // Register clientChannel with the selected worker selector for OP_READ
                            Selector targetSelector = workerSelectors[workerIdx];
                            targetSelector.wakeup(); // wake up worker’s select if blocked
                            ClientContext ctx = new ClientContext();
                            ctx.selector = targetSelector;
                            SelectionKey selectionKey = clientChannel.register(targetSelector, SelectionKey.OP_READ, ctx);
                            ctx.selectionKey = selectionKey;
                            clientChannel.register(targetSelector, SelectionKey.OP_READ, ctx);
                            // Track last active time for timeouts
                            connectionLastActive.put(clientChannel, System.currentTimeMillis());
                        }
                    }
                }
            }
        } catch (IOException e) {
            CabinLogger.error("Boss selector error: " + e.getMessage(), e);
        } finally {
            // Signal shutdown to workers
            for (Selector sel : workerSelectors) {
                sel.wakeup();
            }
            isStopped = true;
        }
    }

    /**
     * Each worker loop processes OP_READ / OP_WRITE for its assigned channels
     **/
    private void runWorkerLoop(int index) {
        Selector workerSelector = workerSelectors[index];
        try {
            while (isRunning) {
                int nReady = workerSelector.select(500);
                if (nReady == 0) {
                    // Optionally handle idle timeouts
                    closeIdleConnections();
                    continue;
                }

                Set<SelectionKey> keys = workerSelector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (!key.isValid()) continue;

                    // Attachment holds per-connection state
                    ClientContext context = (ClientContext) key.attachment();
                    SocketChannel clientChannel = (SocketChannel) key.channel();

                    try {
                        if (key.isReadable()) {
                            // Disable read interest until processing is done
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                            readWorkerPool.submitTask(() -> handleRead(clientChannel, context, workerSelector));
                        }

                        if (key.isValid() && key.isWritable()) {
                            // Handle pending writes
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            writeWorkerPool.submitTask(() -> handleWrite(clientChannel, context, workerSelector));
                        }
                    } catch (CancelledKeyException e) {
                        // Key was cancelled, likely due to closeConnection
                        CabinLogger.warn("Cancelled key for client: " + clientChannel.getRemoteAddress());
                    }
                }
            }
        } catch (IOException e) {
            CabinLogger.error("Worker selector " + index + " error: " + e.getMessage(), e);
        } finally {
            // Clean up if needed
            try {
                workerSelector.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Read handler submitted to readWorkerPool
     **/
    private void handleRead(SocketChannel clientChannel, ClientContext context, Selector workerSelector) {
        ByteBuffer buffer = null;
        try {
            // Acquire buffer from pool
            buffer = bufferPool.acquire();

            int bytesRead = clientChannel.read(buffer);
            if(bytesRead > 0) {
                buffer.flip(); // Prepare buffer for reading
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                context.requestBuffer.write(data);
                connectionLastActive.put(clientChannel, System.currentTimeMillis());

                if(isRequestComplete(context.requestBuffer.toByteArray())) {
                    // Process the HTTP request
                    processHttpRequest(clientChannel, context);
                } else {
                    // Not complete yet, re-enable read interest
                    workerSelector.wakeup();
                    SelectionKey key = clientChannel.keyFor(workerSelector);
                    if(key != null && key.isValid()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                    }
                }
            } else if(bytesRead < 0) {
                closeConnection(clientChannel, workerSelector);
            } else {
                // No data read, re-enable read interest
                workerSelector.wakeup();
                SelectionKey key = clientChannel.keyFor(workerSelector);
                if(key != null && key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                }
            }
        } catch (IOException e) {
            CabinLogger.error("Error reading from client: " + e.getMessage(), e);
            closeConnection(clientChannel, workerSelector);
        } finally {
            // Always return buffer to pool
            if (buffer != null) {
                bufferPool.release(buffer);
            }
        }
    }

    /**
     * Write handler submitted to writeWorkerPool
     **/
    private void handleWrite(SocketChannel clientChannel, ClientContext context, Selector workerSelector) {
        try {
            // Assuming context.pendingResponse holds the ByteBuffer to write
            ByteBuffer writeBuf = context.getPendingResponse();
            if (writeBuf != null) {
                clientChannel.write(writeBuf);
                if (!writeBuf.hasRemaining()) {
                    // Finished writing: clear pending, re‐enable read or close if needed
                    context.clearPendingResponse();
                    // Optionally keep reading
                    workerSelector.wakeup();
                    SelectionKey key = clientChannel.keyFor(workerSelector);
                    if (key != null && key.isValid()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                    }
                } else {
                    // Still more to write: re‐register for OP_WRITE
                    workerSelector.wakeup();
                    SelectionKey key = clientChannel.keyFor(workerSelector);
                    if (key != null && key.isValid()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                }
            }
        } catch (IOException e) {
            CabinLogger.error("Error writing to client: " + e.getMessage(), e);
            closeConnection(clientChannel, workerSelector);
        }
    }

    /**
     * Processes an HTTP request (parsing headers/body, invoking router)
     **/
    private void processHttpRequest(SocketChannel clientChannel, ClientContext context) {
        try {
            byte[] requestData = context.requestBuffer.toByteArray();
            Request request = new Request(requestData);
            Response response = new Response(clientChannel);

            // Final 404 handler
            Handler finalHandler = (req, res) -> {
                res.setStatusCode(404);
                res.writeBody("Not Found");
                res.send();
            };

            // Build middleware chain from middlewareStack
            MiddlewareChain chain = new MiddlewareChain(middlewareStack, finalHandler);
            chain.next(request, response);

            int statusCode = response.getStatusCode();
            String path = request.getPath();

            // End profiling
            if (profilerEnabled) {
                ServerProfiler.INSTANCE.endRequest(path, statusCode);
            }

            // After building response, queue it for writing
            ByteBuffer respBuffer = response.toByteBuffer();
            context.setPendingResponse(respBuffer);

            // Use the selector stored in context
            Selector sel = context.selector;
            SelectionKey key = context.selectionKey;

            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                sel.wakeup();
            }
            // Reset requestBuffer for next request
            context.requestBuffer.reset();
        } catch (IOException e) {
            CabinLogger.error("Error processing request: " + e.getMessage(), e);
            sendInternalServerError(clientChannel, workerSelectors[nextWorkerIndex]);
        } catch (Throwable t) {
            CabinLogger.error("Unhandled error: " + t.getMessage(), t);
            GlobalExceptionHandler.handleException(t, new Response(clientChannel));
            sendInternalServerError(clientChannel, workerSelectors[nextWorkerIndex]);
        }
    }

    /**
     * Checks HTTP request completeness
     **/
    private boolean isRequestComplete(byte[] requestData) {
        String s = new String(requestData, StandardCharsets.ISO_8859_1);
        if (!s.contains("\r\n\r\n")) return false;
        Matcher m = Pattern.compile("Content-Length: (\\d+)", Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) {
            int contentLen = Integer.parseInt(m.group(1));
            int headerEnd = s.indexOf("\r\n\r\n") + 4;
            return requestData.length >= headerEnd + contentLen;
        }
        if (s.contains("Transfer-Encoding: chunked")) {
            return s.endsWith("0\r\n\r\n");
        }
        return true;
    }

    /**
     * Closes a client connection and cancels its key
     **/
    private void closeConnection(SocketChannel channel, Selector selector) {
        try {
            SelectionKey key = channel.keyFor(selector);
            if (key != null) key.cancel();
            connectionLastActive.remove(channel);
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException e) {
            CabinLogger.error("Error closing connection: " + e.getMessage(), e);
        }
    }

    /**
     * Periodically close idle connections
     **/
    private void closeIdleConnections() {
        long now = System.currentTimeMillis();
        for (Map.Entry<SocketChannel, Long> entry : connectionLastActive.entrySet()) {
            SocketChannel ch = entry.getKey();
            long last = entry.getValue();
            if (now - last > idleConnectionTimeoutMillis) {
                closeConnection(ch, ch.keyFor(workerSelectors[nextWorkerIndex]).selector());
            }
        }
    }

    /**
     * Adds a router as middleware
     **/
    public void use(Router router) {
        if (router == null) throw new IllegalArgumentException("Router cannot be null");
        middlewareStack.add(router);
    }

    /**
     * Adds a router with a prefix (mount)
     **/
    public void use(String path, Router router) {
        if (router == null) throw new IllegalArgumentException("Router cannot be null");
        Router mounted = new Router();
        mounted.use(path, router);
        middlewareStack.add(mounted);
    }

    /**
     * Adds any generic middleware
     **/
    public void use(Middleware m) {
        if (m == null) throw new IllegalArgumentException("Middleware cannot be null");
        middlewareStack.add(m);
    }

    /**
     * Sends 500 on exception
     **/
    private void sendInternalServerError(SocketChannel clientChannel, Selector selector) {
        try {
            Response resp = new Response(clientChannel);
            resp.setStatusCode(500);
            resp.writeBody("Internal Server Error");
            resp.send();
        } catch (Exception e) {
            CabinLogger.error("Error sending 500: " + e.getMessage(), e);
        }
    }

    /**
     * Initiates shutdown: closes boss & worker selectors, channels, pools
     **/
    private void shutdown() {
        CabinLogger.info("Initiating server shutdown...");

        isRunning = false;
        bossSelector.wakeup();
        for (Selector sel : workerSelectors) {
            sel.wakeup();
        }

        // Close all selectors
        try {
            if (bossSelector.isOpen()) bossSelector.close();
        } catch (IOException e) {
            CabinLogger.error("Error closing boss selector: " + e.getMessage(), e);
        }
        for (Selector sel : workerSelectors) {
            try {
                if (sel.isOpen()) sel.close();
            } catch (IOException e) {
                CabinLogger.error("Error closing worker selector: " + e.getMessage(), e);
            }
        }

        // Close all channels
        for (SocketChannel ch : connectionLastActive.keySet()) {
            try {
                if (ch.isOpen()) {
                    ch.close();
                }
            } catch (IOException e) {
                CabinLogger.error("Error closing channel on shutdown: " + e.getMessage(), e);
            }
        }
        connectionLastActive.clear();

        // Shutdown worker pools
        try {
            readWorkerPool.shutdown();
            writeWorkerPool.shutdown();
        } catch (Exception e) {
            CabinLogger.error("Error shutting down pools: " + e.getMessage(), e);
        }

        // Stop profiler
        if (profilerEnabled) {
            ServerProfiler.INSTANCE.stop();
        }

        isStopped = true;
        CabinLogger.info("Server shutdown complete.");
    }

    /**
     * Stops server gracefully within timeoutMillis
     **/
    public boolean stop(long timeoutMillis) {
        CabinLogger.info("Stop signal received. Shutting down server...");
        long start = System.currentTimeMillis();
        shutdown(); // sets isRunning=false and wakes selectors

        // Wait for threads to end
        long deadline = start + timeoutMillis;
        try {
            bossThread.join(Math.max(0, deadline - System.currentTimeMillis()));
            for (Thread wt : workerThreads) {
                wt.join(Math.max(0, deadline - System.currentTimeMillis()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CabinLogger.error("Interrupted while stopping server", e);
        }
        return isStopped;
    }

    public boolean stop() {
        return stop(5000);
    }

    public int getPort() {
        return port;
    }

    /**
     * Sets up profiler dashboard routes
     **/
    private void setupDashboard() {
        DashboardReporter dashboardReport = new DashboardReporter();
        ServerProfiler.INSTANCE.addReporter(dashboardReport);
        this.use(dashboardReport.getRouter());
    }

    /**
     * Simple per‐connection state
     */
    private static class ClientContext {
        ByteArrayOutputStream requestBuffer = new ByteArrayOutputStream(8192);
        ByteBuffer pendingResponse; // store outbound data for OP_WRITE
        Selector selector;
        SelectionKey selectionKey;

        void setPendingResponse(ByteBuffer buf) {
            this.pendingResponse = buf;
        }

        ByteBuffer getPendingResponse() {
            return pendingResponse;
        }

        void clearPendingResponse() {
            pendingResponse = null;
        }
    }
}
