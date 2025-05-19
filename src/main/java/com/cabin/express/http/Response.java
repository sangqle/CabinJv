package com.cabin.express.http;

import com.cabin.express.loggger.CabinLogger;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * Represents an HTTP response.
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */
public class Response {
    private int statusCode = 200;
    private ConcurrentHashMap
            <String, String> headers = new ConcurrentHashMap<>();
    private ConcurrentHashMap
            <String, String> cookies = new ConcurrentHashMap<>();
    private StringBuilder body = new StringBuilder();
    private final SocketChannel clientChannel;
    private boolean compressionEnabled = false;
    private ByteArrayOutputStream bufferOut;

    private static final Gson gson = new Gson();

    private static final String DEFAULT_DOMAIN = "";
    private static final String DEFAULT_PATH = "/";
    private static final String DEFAULT_EXPIRES = "";
    private static final boolean DEFAULT_HTTP_ONLY = false;
    private static final boolean DEFAULT_SECURE = false;

    public Response(SocketChannel clientChannel) {
        this.clientChannel = clientChannel;
        this.bufferOut = new ByteArrayOutputStream();
    }

    /**
     * Enables compression for this response.
     * Instead of switching to blocking mode, just set the flag.
     */
    public void enableCompression() {
        this.compressionEnabled = true;
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
     * Writes binary data to the response body.
     * This is particularly useful for serving files and other binary content.
     *
     * @param buffer The byte array containing the data to write
     * @param offset The starting position in the buffer
     * @param length The number of bytes to write
     */
    public void write(byte[] buffer, int offset, int length) {
        try {
            // Convert the current StringBuilder content to bytes if needed
            if (body.length() > 0) {
                if (bufferOut == null) {
                    bufferOut = new ByteArrayOutputStream();
                }
                bufferOut.write(body.toString().getBytes(StandardCharsets.UTF_8));
                body = new StringBuilder(); // Reset the StringBuilder
            }

            // Write the binary data to the buffer
            if (bufferOut == null) {
                bufferOut = new ByteArrayOutputStream();
            }
            bufferOut.write(buffer, offset, length);
        } catch (IOException e) {
            CabinLogger.error("Error writing binary data to response: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the response to the client.
     */
    public void send() {
        try {
            StringBuilder hdr = new StringBuilder()
                    .append("HTTP/1.1 ").append(statusCode).append(" ")
                    .append(getStatusMessage(statusCode)).append("\r\n");

            byte[] responseBodyBytes;

            // Determine the response body source: bufferOut (binary) or body (text)
            byte[] rawBodyBytes;
            if (bufferOut != null && bufferOut.size() > 0) {
                rawBodyBytes = bufferOut.toByteArray();
            } else {
                rawBodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            }

            if (compressionEnabled) {
                // Compress the body using GZIP in memory
                ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
                try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedOut)) {
                    gzipOut.write(rawBodyBytes);
                }
                responseBodyBytes = compressedOut.toByteArray();
                headers.put("Content-Encoding", "gzip");
            } else {
                responseBodyBytes = rawBodyBytes;
            }

            hdr.append("Content-Length: ").append(responseBodyBytes.length).append("\r\n");
            headers.forEach((k, v) -> hdr.append(k).append(": ").append(v).append("\r\n"));
            cookies.values().forEach(c -> hdr.append("Set-Cookie: ").append(c).append("\r\n"));
            hdr.append("\r\n");

            ByteBuffer headerBuf = ByteBuffer.wrap(hdr.toString().getBytes(StandardCharsets.UTF_8));
            ByteBuffer bodyBuf = ByteBuffer.wrap(responseBodyBytes);

            while (headerBuf.hasRemaining()) {
                clientChannel.write(headerBuf);
            }

            while (bodyBuf.hasRemaining()) {
                clientChannel.write(bodyBuf);
            }
        } catch (IOException e) {
            String msg = e.getMessage();
            if (!"Broken pipe".equals(msg) && !"Connection reset".equals(msg)) {
                CabinLogger.error("Error sending response: " + msg, e);
            }
        } finally {
            if (bufferOut != null) {
                bufferOut.reset();
            }
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
            if(content instanceof String) {
                writeBody((String) content);
            } else if (content instanceof byte[]) {
                write((byte[]) content, 0, ((byte[]) content).length);
            } else {
                writeBody(content);
            }
        } catch (Throwable e) {
            CabinLogger.error("Error sending response: " + e.getMessage(), e);
        }
        send();
    }

    private String getStatusMessage(int statusCode) {
        return HttpStatusCode.getStatusMessage(statusCode);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public int getStatusCode() {
        return statusCode;
    }
}