package com.cabin.express.server;

import com.cabin.express.CabinLogger;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.router.Router;

import java.io.*;
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

    public void listen(int port) throws IOException {
        initializeServer(port);

        CabinLogger.info("Server started on port " + port);

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
                    CabinLogger.error("Error handling key: " + e.getMessage(), e);
                    key.cancel(); // Cancel key to prevent further processing
                    try {
                        key.channel().close();
                    } catch (IOException ex) {
                        CabinLogger.error("Error closing channel: " + ex.getMessage(), ex);
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
        CabinLogger.info("Accepted new connection from " + clientChannel.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                // Client closed the connection
                CabinLogger.info("Client disconnected: " + clientChannel.getRemoteAddress());
                clientChannel.close();
                key.cancel();
                return;
            } else if (bytesRead == 0) {
                // No data read, but connection is still open
                CabinLogger.debug("No data received from: " + clientChannel.getRemoteAddress());
                return;
            }

            // Convert the ByteBuffer into an InputStream
            buffer.flip(); // Prepare the buffer for reading
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            try (InputStream inputStream = new ByteArrayInputStream(data)) {
                // Parse the HTTP request
                Request request = new Request(inputStream);

                // Prepare the response object
                Response response = new Response(clientChannel);

                // Route the request using all registered routers
                boolean handled = false;
                for (Router router : routers) {
                    if (router.routeRequest(request, response)) {
                        handled = true;
                        break;
                    }
                }

                if (!handled) {
                    // Respond with a 404 Not Found
                    response.setStatusCode(404);
                    response.writeBody("Not Found");
                    response.send();
                }

            }
        } catch (Exception e) {
            CabinLogger.error("Error processing request: " + e.getMessage(), e);

            // Respond with an internal server error
            try (OutputStream outputStream = clientChannel.socket().getOutputStream()) {
                PrintWriter writer = new PrintWriter(outputStream);
                writer.println("HTTP/1.1 500 Internal Server Error\r\n");
                writer.println("Content-Length: 0\r\n");
                writer.println();
                writer.flush();
            } catch (IOException ioException) {
                CabinLogger.error("Error sending 500 response: " + ioException.getMessage(), ioException);
            } finally {
                clientChannel.close();
                key.cancel();
            }
        }
    }

    public void use(Router router) {
        routers.add(router);
    }
}
