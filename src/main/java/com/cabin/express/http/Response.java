package com.cabin.express.http;

import com.cabin.express.loggger.CabinLogger;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP response.
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */
public class Response {
    private int statusCode = 200;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> cookies = new HashMap<>();
    private StringBuilder body = new StringBuilder();
    private final SocketChannel clientChannel;

    private static final Gson gson = new Gson();

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
    public void writeBody(Object content) throws IOException {
        setHeader("Content-Type", "application/json");
        body.append(content != null ? gson.toJson(content) : "");
    }

    /**
     * Writes the specified object as JSON to the response body.
     *
     * @param content The object to write as JSON.
     * @param headers The headers to set in the response.
     */
    public void send() {
        try {
            // Build status line and headers
            StringBuilder headersBuilder = new StringBuilder();
            String statusMessage = getStatusMessage(statusCode);
            headersBuilder.append(String.format("HTTP/1.1 %d %s\r\n", statusCode, statusMessage));

            headers.forEach((key, value) -> headersBuilder.append(String.format("%s: %s\r\n", key, value)));
            cookies.forEach((key, value) -> headersBuilder.append(String.format("Set-Cookie: %s=%s\r\n", key, value)));

            // Convert body to bytes using UTF-8
            byte[] bodyBytes = body != null ? body.toString().getBytes(StandardCharsets.UTF_8) : new byte[0];
            headersBuilder.append(String.format("Content-Length: %d\r\n", bodyBytes.length));
            headersBuilder.append("\r\n");

            // Write headers
            ByteBuffer headerBuffer = ByteBuffer.wrap(headersBuilder.toString().getBytes(StandardCharsets.UTF_8));
            ByteBuffer bodyBuffer = ByteBuffer.wrap(bodyBytes);

            if (!clientChannel.isOpen()) {
                CabinLogger.error(String.format("Client channel is closed: %s", clientChannel), null);
                return;
            }

            while (headerBuffer.hasRemaining()) {
                clientChannel.write(headerBuffer);
            }
            while (bodyBuffer.hasRemaining()) {
                clientChannel.write(bodyBuffer);
            }
        } catch (IOException e) {
            if ("Broken pipe".equals(e.getMessage())) {
                CabinLogger.error("Client closed connection: " + e.getMessage(), e);
            } else {
                CabinLogger.error("Error sending response: " + e.getMessage(), e);
            }
        } catch (Throwable e) {
            CabinLogger.error("Unexpected error sending response: " + e.getMessage(), e);
        }
    }


    /**
     * Write specified object as JSON to the response body.
     *
     * @param content content content content content content content content
     * @return
     */
    public void send(Object content) {
        try {
            writeBody(content);
        } catch (Throwable e) {
            CabinLogger.error("Error sending response: " + e.getMessage(), e);
        }
        send();
    }

    private String getStatusMessage(int statusCode) {
        return HttpStatusCode.getStatusMessage(statusCode);
    }
}