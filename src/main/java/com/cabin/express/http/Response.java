package com.cabin.express.http;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {
    private int statusCode = 200;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> cookies = new HashMap<>();
    private StringBuilder body = new StringBuilder();
    private final SocketChannel clientChannel;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_DOMAIN = "";
    private static final String DEFAULT_PATH = "/";
    private static final String DEFAULT_EXPIRES = "";
    private static final boolean DEFAULT_HTTP_ONLY = false;
    private static final boolean DEFAULT_SECURE = false;

    public Response(SocketChannel clientChannel) {
        this.clientChannel = clientChannel;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setCookie(String name, String value) {
        setCookie(name, value, DEFAULT_DOMAIN, DEFAULT_PATH, DEFAULT_EXPIRES, DEFAULT_HTTP_ONLY, DEFAULT_SECURE);
    }

    public void setCookie(String name, String value, String domain) {
        setCookie(name, value, domain, DEFAULT_PATH, DEFAULT_EXPIRES, DEFAULT_HTTP_ONLY, DEFAULT_SECURE);
    }

    public void setCookie(String name, String value, String domain, String path) {
        setCookie(name, value, domain, path, DEFAULT_EXPIRES, DEFAULT_HTTP_ONLY, DEFAULT_SECURE);
    }

    public void setCookie(String name, String value, String domain, String path, String expires) {
        setCookie(name, value, domain, path, expires, DEFAULT_HTTP_ONLY, DEFAULT_SECURE);
    }

    public void setCookie(String name, String value, String domain, String path, String expires, boolean httpOnly) {
        setCookie(name, value, domain, path, expires, httpOnly, DEFAULT_SECURE);
    }

    /**
     * Sets a cookie with the specified attributes.
     *
     * @param name     The name of the cookie.
     * @param value    The value of the cookie.
     * @param domain   The domain for the cookie.
     * @param path     The path for the cookie.
     * @param expires  The expiration date for the cookie.
     * @param httpOnly Whether the cookie is HTTP only.
     * @param secure   Whether the cookie is secure.
     */
    public void setCookie(String name, String value, String domain, String path, String expires, boolean httpOnly, boolean secure) {
        StringBuilder cookieBuilder = new StringBuilder();
        cookieBuilder.append(name).append("=").append(value);
        if (domain != null && !domain.isEmpty()) {
            cookieBuilder.append("; Domain=").append(domain);
        }
        if (path != null && !path.isEmpty()) {
            cookieBuilder.append("; Path=").append(path);
        }
        if (expires != null && !expires.isEmpty()) {
            cookieBuilder.append("; Expires=").append(expires);
        }
        if (httpOnly) {
            cookieBuilder.append("; HttpOnly");
        }
        if (secure) {
            cookieBuilder.append("; Secure");
        }
        cookies.put(name, cookieBuilder.toString());
    }

    /**
     * Clears a cookie with the specified name.
     *
     * @param name The name of the cookie to clear.
     */
    public void clearCookie(String name, String domain, String path) {
        setCookie(name, "", domain, path, "Thu, 01 Jan 1970 00:00:00 GMT", DEFAULT_HTTP_ONLY, DEFAULT_SECURE);
    }

    public void writeBody(String content) {
        body.append(content);
    }

    /**
     * Writes the specified object as JSON to the response body.
     *
     * @param content The object to write as JSON.
     * @throws IOException if an error occurs while writing the JSON.
     */
    public void writeJsonBody(Object content) throws IOException {
        setHeader("Content-Type", "application/json");
        body.append(objectMapper.writeValueAsString(content));
    }

    /**
     * Sends the response to the client.
     *
     * @throws IOException if an error occurs while sending the response.
     */
    public void send() throws IOException {
        // Write headers and status line
        StringBuilder headersBuilder = new StringBuilder();

        // Status line
        String statusMessage = getStatusMessage(statusCode);
        headersBuilder.append(String.format("HTTP/1.1 %d %s\r\n", statusCode, statusMessage));

        // Headers
        headers.forEach((key, value) -> headersBuilder.append(String.format("%s: %s\r\n", key, value)));

        // Cookies
        cookies.forEach((key, value) -> headersBuilder.append(String.format("Set-Cookie: %s=%s\r\n", key, value)));

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