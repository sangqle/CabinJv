package com.cabin.express.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class CabinResponse {
    private int statusCode = 200;
    private Map<String, String> headers = new HashMap<>();
    private StringBuilder body = new StringBuilder();
    private final SocketChannel clientChannel;

    public CabinResponse(SocketChannel clientChannel) {
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
        // Build the response string
        StringBuilder response = new StringBuilder();

        // Write the status line
        String statusMessage = getStatusMessage(statusCode);
        response.append(String.format("HTTP/1.1 %d %s\r\n", statusCode, statusMessage));

        // Write headers
        headers.forEach((key, value) -> response.append(String.format("%s: %s\r\n", key, value)));

        // Write the Content-Length header
        response.append(String.format("Content-Length: %d\r\n", body.length()));

        // End of headers
        response.append("\r\n");

        // Write the body
        response.append(body);

        // Write the response to the SocketChannel using ByteBuffer
        ByteBuffer buffer = ByteBuffer.wrap(response.toString().getBytes());
        while (buffer.hasRemaining()) {
            clientChannel.write(buffer);
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