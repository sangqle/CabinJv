package com.cabin.express;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.InetSocketAddress;
import java.util.Iterator;

public class CabinJNIO {
    private Selector selector;

    public void listen(int port) throws IOException {
        // Open a selector
        selector = Selector.open();

        // Open a server socket channel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false); // Set to non-blocking

        // Register the channel with the selector for accepting connections
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started on port " + port);

        // Event loop
        while (true) {
            selector.select(); // Blocks until at least one channel is ready
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (key.isAcceptable()) {
                    handleAccept(serverChannel);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void handleAccept(ServerSocketChannel serverChannel) throws IOException {
        // Accept the connection
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        System.out.println("Accepted connection from " + clientChannel.getRemoteAddress());

        // Register the new channel for reading
        clientChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024));
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        int bytesRead = clientChannel.read(buffer);
        if (bytesRead == -1) {
            clientChannel.close();
            return;
        }

        // Flip the buffer for reading
        buffer.flip();
        String request = new String(buffer.array(), 0, buffer.limit());
        System.out.println("Received request:\n" + request);

        // Write a simple response
        String response = "HTTP/1.1 200 OK\r\n" + "Content-Type: text/plain\r\n" + "Content-Length: 13\r\n" + "\r\n" + "Hello, World!";
        clientChannel.write(ByteBuffer.wrap(response.getBytes()));

        // Clear buffer for the next read
        buffer.clear();
        clientChannel.close();
    }

    public static void main(String[] args) throws IOException {
        CabinJNIO server = new CabinJNIO();
        server.listen(8080);
    }
}
