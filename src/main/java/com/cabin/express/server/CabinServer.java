package com.cabin.express.server;

import com.cabin.express.CabinLogger;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.router.Router;
import com.cabin.express.worker.CabinWorkerPool;

import java.io.*;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A simple HTTP server using Java NIO.
 * Author: Sang Le
 * Created: 2024-12-24
 */

public class CabinServer {
    private Selector selector;
    private final List<Router> routers = new ArrayList<>();

    private final int port;
    private final int threadPoolSize;
    private final CabinWorkerPool workerPool;

    protected CabinServer(int port, int threadPoolSize) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
        this.workerPool = new CabinWorkerPool(threadPoolSize); // Initialize with configured size
    }

    public void start() throws IOException {
        initializeServer();

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

    private void handleAccept(ServerSocketChannel serverChannel) throws IOException {
        // Accept the incoming connection
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);

        // Register the new channel for reading
        clientChannel.register(selector, SelectionKey.OP_READ);
        CabinLogger.info("Accepted new connection from " + clientChannel.getRemoteAddress());
    }

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
                CabinLogger.info("Client closed connection: " + clientChannel.getRemoteAddress());
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
        } catch (Exception e) {
            CabinLogger.error("Unexpected error handling read event: " + e.getMessage(), e);
            closeChannelAndCancelKey(clientChannel, key);
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

    public void use(Router router) {
        routers.add(router);
    }
}
