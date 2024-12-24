package com.cabin.express.server;

import com.cabin.express.CabinJLogger;
import com.cabin.express.http.CabinRequest;
import com.cabin.express.http.CabinResponse;
import com.cabin.express.router.CabinRouter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.InetSocketAddress;
import java.util.Iterator;

/**
 * A simple HTTP server using Java NIO.
 * Author: Sang Le
 * Created: 2024-12-24
 */

public class CabinJServer {
    private Selector selector;
    private CabinRouter router;

    public void listen(int port) throws IOException {
        initializeServer(port);

        CabinJLogger.info("Server started on port " + port);

        // Event loop
        while (true) {
            selector.select(); // Blocks until at least one channel is ready
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                try {
                    if (key.isAcceptable()) {
                        handleAccept((ServerSocketChannel) key.channel());
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                } catch (Exception e) {
                    CabinJLogger.error("Error handling key: " + e.getMessage(), e);
                    key.cancel(); // Cancel key to prevent further processing
                    try {
                        key.channel().close();
                    } catch (IOException ex) {
                        CabinJLogger.error("Error closing channel: " + ex.getMessage(), ex);
                    }
                }
            }
        }
    }

    private void initializeServer(int port) throws IOException {
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
        CabinJLogger.info("Accepted new connection from " + clientChannel.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                // Client closed the connection
                CabinJLogger.info("Client disconnected: " + clientChannel.getRemoteAddress());
                clientChannel.close();
                key.cancel();
                return;
            } else if (bytesRead == 0) {
                // No data read, but connection is still open
                CabinJLogger.debug("No data received from: " + clientChannel.getRemoteAddress());
                return;
            }

            // Convert the ByteBuffer into an InputStream
            buffer.flip(); // Prepare the buffer for reading
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                // Parse the HTTP request
                CabinRequest request = new CabinRequest(inputStream);

                // Prepare the response object
                CabinResponse response = new CabinResponse(clientChannel);

                // Route the request
                router.handle(request, response);
            }
        } catch (Exception e) {
            CabinJLogger.error("Error processing request: " + e.getMessage(), e);

            // Respond with an internal server error
            try (OutputStream outputStream = clientChannel.socket().getOutputStream()) {
                PrintWriter writer = new PrintWriter(outputStream);
                writer.println("HTTP/1.1 500 Internal Server Error\r\n");
                writer.println("Content-Length: 0\r\n");
                writer.println();
                writer.flush();
            } catch (IOException ioException) {
                CabinJLogger.error("Error sending 500 response: " + ioException.getMessage(), ioException);
            } finally {
                clientChannel.close();
                key.cancel();
            }
        }
    }

    public void addRoute(CabinRouter router) {
        this.router = router;
    }

    public CabinRouter getRouter() {
        return this.router;
    }
}
