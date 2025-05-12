package com.cabin.express.http;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.stream.NonBlockingOutputStream;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> cookies = new HashMap<>();
    private StringBuilder body = new StringBuilder();
    private OutputStream out;
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
        this.out = bufferOut; // Default to in-memory buffer
    }

    /**
     * Enables compression for this response.
     * When called, this will switch the response to use blocking I/O.
     *
     * @throws IOException if an I/O error occurs
     */
    public void enableCompression() throws IOException {
        this.compressionEnabled = true;
        // Switch to blocking mode when compression is enabled
        if (!clientChannel.isBlocking()) {
            clientChannel.configureBlocking(true);
        }
        // Initialize the real output stream only when needed
        this.out = Channels.newOutputStream(clientChannel);
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
     * Sends the response to the client.
     */
    public void send() {
        try {
            // Build headers
            StringBuilder hdr = new StringBuilder()
                    .append("HTTP/1.1 ").append(statusCode).append(" ")
                    .append(getStatusMessage(statusCode)).append("\r\n");

            // Add Content-Length if we know it (non-compressed case)
            if (!compressionEnabled) {
                byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                hdr.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            }

            headers.forEach((k, v) -> hdr.append(k).append(": ").append(v).append("\r\n"));
            cookies.values().forEach(c -> hdr.append("Set-Cookie: ").append(c).append("\r\n"));
            hdr.append("\r\n");

            if (compressionEnabled) {
                // Blocking I/O path - headers already have Content-Encoding: gzip
                out.write(hdr.toString().getBytes(StandardCharsets.UTF_8));

                // Now write the body
                byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                if (bodyBytes.length > 0) {
                    out.write(bodyBytes);
                }

                // Clean up if using GZIPOutputStream
                if (out instanceof GZIPOutputStream) {
                    ((GZIPOutputStream) out).finish();
                }
                out.flush();

                // Don't close the output stream here as it might close the underlying socket
            } else {
                // Non-blocking I/O path
                ByteBuffer headerBuf = ByteBuffer.wrap(hdr.toString().getBytes(StandardCharsets.UTF_8));
                ByteBuffer bodyBuf = ByteBuffer.wrap(body.toString().getBytes(StandardCharsets.UTF_8));

                // Write headers and body in sequence
                while (headerBuf.hasRemaining()) {
                    clientChannel.write(headerBuf);
                }

                while (bodyBuf.hasRemaining()) {
                    clientChannel.write(bodyBuf);
                }
            }
        } catch (IOException e) {
            // swallow broken-pipe / connection reset
            String msg = e.getMessage();
            if (!"Broken pipe".equals(msg) && !"Connection reset".equals(msg)) {
                CabinLogger.error("Error sending response: " + msg, e);
            }
        } finally {
            // Reset the buffer if we're using in-memory buffering
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
            writeBody(content);
        } catch (Throwable e) {
            CabinLogger.error("Error sending response: " + e.getMessage(), e);
        }
        send();
    }

    private String getStatusMessage(int statusCode) {
        return HttpStatusCode.getStatusMessage(statusCode);
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

}