package com.cabin.express.server;

import com.cabin.express.exception.GlobalExceptionHandler;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.router.Router;
import com.cabin.express.worker.CabinWorkerPool;
import com.sun.management.OperatingSystemMXBean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
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
    private ScheduledFuture<?> resourceLoggingTask;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ThreadLocal<ByteBuffer> bufferPool = ThreadLocal.withInitial(() -> ByteBuffer.allocate(1024));
    private final Map<SocketChannel, Long> connectionLastActive = new ConcurrentHashMap<>();

    private final int port;
    private final CabinWorkerPool workerPool;

    private long peakHeapUsed = 0;
    private long peakNonHeapUsed = 0;
    private long peakUsedPhysicalMemorySize = 0;
    private final long connectionTimeoutMillis; // Timeout threshold (30 seconds)

    /**
     * Creates a new server with the specified port number, default pool size,
     * maximum pool size, and maximum queue capacity.
     *
     * @param port             the port number
     * @param defaultPoolSize  the default number of threads in the thread pool
     * @param maxPoolSize      the maximum number of threads in the thread pool
     * @param maxQueueCapacity the maximum queue capacity
     */
    protected CabinServer(int port, int defaultPoolSize, int maxPoolSize, int maxQueueCapacity, long connectionTimeoutMillis) {
        this.port = port;
        this.workerPool = new CabinWorkerPool(defaultPoolSize, maxPoolSize, maxQueueCapacity);
        this.connectionTimeoutMillis = connectionTimeoutMillis;
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
        while (true) {
            // Wake up the channels that are ready for I/O operations
            int readyChannels = selector.select(connectionTimeoutMillis);

            if (readyChannels == 0) {
                performPeriodicTasks();
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
                        // Disable read events temporarily
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);

                        workerPool.submitTask(() -> {
                            try {
                                handleRead(key);
                            } catch (Exception e) {
                                CabinLogger.error(String.format("Error handling read event: %s", e.getMessage()), e);
                                try {
                                    key.channel().close();
                                } catch (IOException ex) {
                                    CabinLogger.error("Error closing channel", ex);
                                }
                                key.cancel();
                            } finally {
                                if (key.isValid()) {
                                    // Re-enable read events
                                    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                                    key.selector().wakeup(); // Wake up the selector to re-register the key
                                }
                            }
                        });
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
    }

    private void performPeriodicTasks() {
        try {
            if ((resourceLoggingTask == null || resourceLoggingTask.isCancelled() || resourceLoggingTask.isDone()) && !scheduler.isShutdown() && !scheduler.isTerminated()) {
                resourceLoggingTask = scheduler.scheduleAtFixedRate(this::logResourceUsage, 0, 30, TimeUnit.SECONDS);
                CabinLogger.info("Resource logging task scheduled.");
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

    /**
     * Handles reading data from a client connection.
     *
     * @param key the selection key representing the client connection
     * @throws IOException if an I/O error occurs
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        try {

            ByteBuffer buffer = bufferPool.get();
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
                connectionLastActive.put(clientChannel, System.currentTimeMillis()); // Refresh active time
                buffer.flip(); // Prepare buffer for reading
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                handleClientRequest(clientChannel, data);
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
                    channel.close();
                } catch (IOException e) {
                    CabinLogger.error("Error closing idle connection: " + e.getMessage(), e);
                }
                connectionLastActive.remove(channel); // Remove from map
            }
        }
    }

    private void logResourceUsage() {
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
        CabinLogger.info(String.format("+---------------------+---------------------+\n" + "| Metric              | Value               |\n" + "+---------------------+---------------------+\n" + "| Process CPU Load    | %.2f%%              |\n" + "| System CPU Load     | %.2f%%              |\n" + "| Total Physical Mem  | %,d bytes           |\n" + "| Used Physical Mem   | %,d bytes           |\n" + "| Free Physical Mem   | %,d bytes           |\n" + "| Heap Memory Used    | %,d bytes           |\n" + "| Heap Memory Max     | %,d bytes           |\n" + "| Non-Heap Mem Used   | %,d bytes           |\n" + "| Peak Heap Mem Used  | %,d bytes           |\n" + "| Peak Non-Heap Mem   | %,d bytes           |\n" + "| Peak Used Phys Mem  | %,d bytes           |\n" + "+---------------------+---------------------+", processCpuLoad, systemCpuLoad, totalPhysicalMemorySize, usedPhysicalMemorySize, freePhysicalMemorySize, heapUsed, heapMax, nonHeapUsed, peakHeapUsed, peakNonHeapUsed, peakUsedPhysicalMemorySize));

        CabinLogger.info(String.format("+---------------------+---------------------+\n" + "| Worker Pool Metric  | Value               |\n" + "+---------------------+---------------------+\n" + "| Worker Pool Size    | %d                  |\n" + "| Active Threads      | %d                  |\n" + "| Pending Tasks       | %d                  |\n" + "| Largest Pool Size   | %d                  |\n" + "+---------------------+", workerPool.getPoolSize(), workerPool.getActiveThreadCount(), workerPool.getPendingTaskCount(), workerPool.getLargestPoolSize()));
    }
}
