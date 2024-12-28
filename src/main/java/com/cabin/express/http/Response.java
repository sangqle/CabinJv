package com.cabin.express.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {
    private int statusCode = 200;
    private Map<String, String> headers = new HashMap<>();
    private StringBuilder body = new StringBuilder();
    private final SocketChannel clientChannel;

    public Response(SocketChannel clientChannel) {
        this.clientChannel = clientChannel;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public void writeBody(String content) {
        body.append(content);
    }

    public void send () throws IOException {
        // Write headers and status line
        StringBuilder headersBuilder = new StringBuilder();

        // Status line
        String statusMessage = getStatusMessage(statusCode);
        headersBuilder.append(String.format("HTTP/1.1 %d %s\r\n", statusCode, statusMessage));

        // Headers
        headers.forEach((key, value) -> headersBuilder.append(String.format("%s: %s\r\n", key, value)));

        // Content-Length
        int contentLength = body != null ? body.length() : 0;
        headersBuilder.append(String.format("Content-Length: %d\r\n", contentLength));

        // End of headers
        headersBuilder.append("\r\n");

        // Convert headers to bytes
        ByteBuffer headerBuffer = ByteBuffer.wrap(headersBuilder.toString().getBytes(StandardCharsets.UTF_8));

        // Convert body to bytes (if not null)
        ByteBuffer bodyBuffer = body != null ? ByteBuffer.wrap(body.toString().getBytes(StandardCharsets.UTF_8)) : null;

        // Write the headers first
        while (headerBuffer.hasRemaining()) {
            clientChannel.write(headerBuffer);
        }

        // Write the body if it exists
        if (bodyBuffer != null) {
            while (bodyBuffer.hasRemaining()) {
                clientChannel.write(bodyBuffer);
            }
        }
    }

    private String getStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}