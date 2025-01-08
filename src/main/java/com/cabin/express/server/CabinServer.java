package com.cabin.express.server;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.router.Router;
import com.cabin.express.worker.CabinWorkerPool;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.management.OperatingSystemMXBean;


/**
 * A simple HTTP server using Java NIO.
 * Author: Sang Le
 * Created: 2024-12-24
 */

public class CabinServer {
    private Selector selector;
    private final List<Router> routers = new ArrayList<>();

    private Map<String, Boolean> endpointMap = new HashMap<>();
    private final List<Middleware> globalMiddlewares = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final int port;
    private final int defaultPoolSize;
    private final int maxPoolSize;
    private final int maxQueueCapacity;
    private final CabinWorkerPool workerPool;

    protected CabinServer(int port, int defaultPoolSize, int maxPoolSize, int maxQueueCapacity) {
        this.port = port;
        this.defaultPoolSize = defaultPoolSize;
        this.maxPoolSize = maxPoolSize;
        this.maxQueueCapacity = maxQueueCapacity;
        this.workerPool = new CabinWorkerPool(defaultPoolSize, maxPoolSize, maxQueueCapacity);
    }

    /**
     * Start the server
     *
     * @throws IOException if an I/O error occurs
     */
    public void start() throws IOException {
        initializeServer();

        // Schedule resource usage logging every 10 seconds
        scheduler.scheduleAtFixedRate(this::logResourceUsage, 0, 10, TimeUnit.SECONDS);

        CabinLogger.info("Server started on port " + port);

        // Event loop
        while (true) {
            selector.select(); // Blocks until at least one channel is ready
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
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false); // Set to non-blocking

        // Register the channel with the selector for accepting connections
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    /**
     * Handles accepting a new incoming connection.
     *
     * @param serverChannel the server socket channel that is accepting the connection
     * @throws IOException if an I/O error occurs
     */
    private void handleAccept(ServerSocketChannel serverChannel) throws IOException {
        // Accept the incoming connection
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);

        // Register the new channel for reading
        clientChannel.register(selector, SelectionKey.OP_READ);
//        CabinLogger.info("Accepted new connection from " + clientChannel.getRemoteAddress());
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
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            if (!clientChannel.isOpen()) {
                CabinLogger.info("Channel is closed: " + clientChannel.getRemoteAddress());
                return;
            }

            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                // Client closed the connection
//                CabinLogger.info("Client closed connection: " + clientChannel.getRemoteAddress());
                closeChannelAndCancelKey(clientChannel, key);
                return;
            }
            if (bytesRead > 0) {
                buffer.flip(); // Prepare buffer for reading
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

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
                }
            }
        } catch (SocketException e) {
            if ("Connection reset".equals(e.getMessage())) {
                CabinLogger.info("Connection reset by peer: " + e.getMessage());
            } else {
                CabinLogger.error("Socket exception: " + e.getMessage(), e);
            }
            closeChannelAndCancelKey(clientChannel, key);
        } catch (IOException e) {
            CabinLogger.error("Error handling read event: " + e.getMessage(), e);
            closeChannelAndCancelKey(clientChannel, key);
        } catch (Throwable e) {
            CabinLogger.error("Unexpected error handling read event: " + e.getMessage(), e);
            sendInternalServerError(clientChannel);
            closeChannelAndCancelKey(clientChannel, key);
        }
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

    private void logResourceUsage() {
        // Get the OperatingSystemMXBean for system-level metrics
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        // Get the MemoryMXBean for JVM-level metrics
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();

        // 1. System-level metrics
        double processCpuLoad = osBean.getProcessCpuLoad() * 100; // CPU usage of your application
        double systemCpuLoad = osBean.getSystemCpuLoad() * 100;  // CPU usage of the entire system
        long totalPhysicalMemorySize = osBean.getTotalPhysicalMemorySize();
        long freePhysicalMemorySize = osBean.getFreePhysicalMemorySize();
        long usedPhysicalMemorySize = totalPhysicalMemorySize - freePhysicalMemorySize;

        // 2. JVM-level metrics
        long heapUsed = heapMemoryUsage.getUsed();
        long heapMax = heapMemoryUsage.getMax();
        long nonHeapUsed = nonHeapMemoryUsage.getUsed();

        // Log the information
        CabinLogger.info("Resource Usage ----------------------------------------------------------");
        // Log full system metrics
        CabinLogger.info(String.format(
                "+---------------------+---------------------+\n" +
                        "| Metric              | Value               |\n" +
                        "+---------------------+---------------------+\n" +
                        "| Process CPU Load    | %.2f%%              |\n" +
                        "| System CPU Load     | %.2f%%              |\n" +
                        "| Total Physical Mem  | %,d bytes           |\n" +
                        "| Used Physical Mem   | %,d bytes           |\n" +
                        "| Free Physical Mem   | %,d bytes           |\n" +
                        "| Heap Memory Used    | %,d bytes           |\n" +
                        "| Heap Memory Max     | %,d bytes           |\n" +
                        "| Non-Heap Mem Used   | %,d bytes           |\n" +
                        "+---------------------+---------------------+",
                processCpuLoad, systemCpuLoad, totalPhysicalMemorySize, usedPhysicalMemorySize, freePhysicalMemorySize, heapUsed, heapMax, nonHeapUsed));

        CabinLogger.info(String.format(
                "+---------------------+---------------------+\n" +
                        "| Worker Pool Metric  | Value               |\n" +
                        "+---------------------+---------------------+\n" +
                        "| Worker Pool Size    | %d                  |\n" +
                        "| Active Threads      | %d                  |\n" +
                        "| Pending Tasks       | %d                  |\n" +
                        "| Largest Pool Size   | %d                  |\n" +
                        "+---------------------+---------------------+",
                workerPool.getPoolSize(), workerPool.getActiveThreadCount(), workerPool.getPendingTaskCount(), workerPool.getLargestPoolSize()));
    }
}
