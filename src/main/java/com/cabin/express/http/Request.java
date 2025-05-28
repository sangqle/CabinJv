package com.cabin.express.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.reflect.Type;

/**
 * Represents an HTTP request with methods for accessing and parsing request data.
 * <p>
 * This class encapsulates key components of an HTTP request, including the HTTP method,
 * path, headers, query parameters, path parameters, form fields, uploaded files, and the request body.
 * It provides convenient accessor methods for retrieving various aspects of the request, and
 * supports parsing of URL-encoded forms, multipart data, and JSON bodies.
 * </p>
 *
 * <p>
 * <b>Usage:</b> A {@code Request} instance is designed to be used by a single thread
 * handling an individual HTTP request. It is not safe for concurrent use by multiple threads.
 * </p>
 *
 * <ul>
 *   <li>Provides accessors for method, path, headers, parameters, and body.</li>
 *   <li>Parses form data (URL-encoded and multipart) and JSON bodies automatically.</li>
 *   <li>Allows retrieval of uploaded files and form fields.</li>
 *   <li>Supports custom attributes for request-scoped data.</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> Instances of this class are not thread-safe. Do not share a {@code Request}
 * object across multiple threads.
 * </p>
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */
public class Request {

    private String remoteIpAddress;
    private String method;
    private String path;
    private String body;
    private Map<String, Object> bodyAsJson = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private Map<String, String> pathParams = new HashMap<>();
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Map<String, String> formFields = new HashMap<>();
    private Map<String, List<UploadedFile>> uploadedFiles = new HashMap<>();
    private final Map<Class<?>, Object> attributes = new HashMap<>();
    private String baseUrl;

    private static final Gson gson = new Gson();


    public Request(ByteArrayOutputStream byteArrayOutputStream) throws Exception {
        parseRequest(byteArrayOutputStream);
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getPathParams() {
        return pathParams;
    }

    public String getPathParam(String key) {
        return pathParams.get(key);
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }

    public void setPathParams(Map<String, String> pathParams) {
        this.pathParams = pathParams;
    }

    private void parseRequest(ByteArrayOutputStream outputStream) throws Exception {
        try {
            byte[] requestDataBytes = outputStream.toByteArray();
            InputStream inputStream = new ByteArrayInputStream(requestDataBytes);

            String requestLine = readLine(inputStream);
            if (requestLine.isEmpty()) {
                throw new IllegalArgumentException("Invalid HTTP request: Missing request line");
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                throw new IllegalArgumentException("Malformed request line: " + requestLine);
            }

            method = requestParts[0];
            String fullPath = requestParts[1];
            parsePathAndQuery(fullPath);

            // Parse headers until blank line
            String headerLine;
            while (!(headerLine = readLine(inputStream)).isEmpty()) {
                int colonIndex = headerLine.indexOf(":");
                if (colonIndex > 0) {
                    String name = headerLine.substring(0, colonIndex).trim();
                    String value = headerLine.substring(colonIndex + 1).trim();
                    headers.put(name, value);
                }
            }

            int contentLength = 0;
            if (headers.containsKey("Content-Length")) {
                contentLength = Integer.parseInt(headers.get("Content-Length"));
            }

            if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int totalBytesRead = 0;

                while (totalBytesRead < contentLength) {
                    int bytesRead = inputStream.read(bodyBytes, totalBytesRead, contentLength - totalBytesRead);
                    if (bytesRead == -1) {
                        break;
                    }
                    totalBytesRead += bytesRead;
                }

                if (totalBytesRead < contentLength) {
                    throw new IOException("Unexpected end of request body");
                }

                String contentType = headers.getOrDefault("Content-Type", "");
                body = new String(bodyBytes, StandardCharsets.UTF_8);
                if (contentType.contains("application/x-www-form-urlencoded")) {
                    parseFormUrlEncodedBody();
                } else if (contentType.contains("multipart/form-data")) {
                    parseMultipartBody(bodyBytes, contentType);
                } else if (contentType.contains("application/json")) {
                    parseJsonBody();
                }
            }

        } catch (Throwable ex) {
            throw new Exception("Failed to parse request: " + ex.getMessage(), ex);
        }
    }


    /**
     * Reads a single line from an InputStream without extra buffering.
     * It reads byte-by-byte until it finds a CRLF (i.e. "\r\n") sequence.
     */
    private String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        while ((c = inputStream.read()) != -1) {
            if (c == '\r') {
                int next = inputStream.read();
                if (next == '\n') {
                    break;  // End of line reached
                }
                // If \r not followed by \n, include both characters
                buffer.write(c);
                buffer.write(next);
            } else {
                buffer.write(c);
            }
        }
        return buffer.toString(StandardCharsets.ISO_8859_1).trim();
    }

    // **URL-Encoded Form Body Parser**
    private void parseFormUrlEncodedBody() {
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                formFields.put(keyValue[0], keyValue[1]);
            }
        }
    }

    // **Multipart Body Parser (Using External MultipartParser Class)**
    private void parseMultipartBody(byte[] data, String contentType) throws Exception {
        MultipartParser parser = new MultipartParser(data, contentType);
        formFields.putAll(parser.getFormFields());
        uploadedFiles.putAll(parser.getUploadedFiles());
    }

    // **JSON Body Parser**
    private void parseJsonBody() {
        try {
            bodyAsJson = gson.fromJson(body, Map.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON body");
        }
    }

    private String extractField(String contentDisposition, String key) {
        String[] parts = contentDisposition.split(";");
        for (String part : parts) {
            if (part.trim().startsWith(key + "=")) {
                return part.split("=")[1].replace("\"", "").trim();
            }
        }
        return null;
    }

    /**
     * Get uploaded file as an object
     *
     * @param fieldName Name of the field
     * @return UploadedFile containing filename, content type, and byte array data
     */
    public List<UploadedFile> getUploadedFile(String fieldName) {
        return uploadedFiles.get(fieldName);
    }

    /**
     * Get form field value
     *
     * @param fieldName Name of the field
     * @return Value of the form field
     */
    public String getFormField(String fieldName) {
        return formFields.get(fieldName);
    }

    public List<String> getFormFields() {
        return new ArrayList<>(formFields.keySet());
    }

    private void parsePathAndQuery(String fullPath) throws Exception {
        String[] parts = fullPath.split("\\?", 2);
        path = parts[0];

        if (parts.length > 1) {
            String queryString = parts[1];
            String[] queryPairs = queryString.split("&");
            for (String pair : queryPairs) {
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()) : "";
                queryParams.put(key, value);
            }
        }
    }

    private void parseBodyAsJson() {
        if (body != null && headers.containsKey("Content-Type") && headers.get("Content-Type").toLowerCase().contains("application/json")) {
            try {
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse JSON body: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Get the request body as a string
     *
     * @return The request body as a string
     */
    public Map<String, Object> getBody() {
        return bodyAsJson;
    }

    /**
     * Get the request body as a string
     *
     * @return The request body as a string
     */
    public String getBodyAsString() {
        return body;
    }

    /**
     * Parses the request body as an object of the specified class.
     *
     * @param <T>     The type of the object to parse the body as.
     * @param typeOfT The class of the object to parse the body as.
     * @return The parsed object, or null if the body is null or not JSON.
     * @throws IllegalArgumentException if the body cannot be parsed as the specified class.
     */
    public <T> T getBodyAs(Type typeOfT) {
        if (body != null && headers.containsKey("content-type") &&
                headers.get("content-type").toLowerCase().contains("application/json")) {
            try {
                return gson.fromJson(body, typeOfT);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse JSON body: " + e.getMessage(), e);
            }
        }
        return null;
    }

    public <T> T getBodyAs(Class<T> clazz) {
        if (body != null && headers.containsKey("content-type") &&
                headers.get("content-type").toLowerCase().contains("application/json")) {
            try {
                // Use a new Gson instance or ensure thread-safety
                return gson.fromJson(body, clazz);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse JSON body: " + e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Get query string
     *
     * @return Query string
     */
    public String getQueryString() {
        StringBuilder queryString = new StringBuilder();
        queryParams.forEach((key, value) -> queryString.append(key).append("=").append(value).append("&"));
        return queryString.toString();
    }

    /**
     * Get path parameter as integer
     *
     * @param key
     * @param defaultValue
     * @return Integer value of the path parameter or the default value if not found or invalid
     */
    public Integer getPathParamAsInt(String key, Integer defaultValue) {
        String value = pathParams.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * @param key
     * @param defaultValue
     * @return Long value of the path parameter or the default value if not found or invalid
     */
    public Long getPathParamAsLong(String key, Long defaultValue) {
        String value = pathParams.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get query parameter
     *
     * @param key
     * @return Value of the query parameter or null if not found
     */
    public String getQueryParam(String key) {
        return queryParams.get(key);
    }

    /**
     * Get query parameter as integer
     *
     * @param key
     * @param defaultValue
     * @return Integer value of the query parameter or the default value if not found or invalid
     */
    public Integer getQueryParamAsInt(String key, Integer defaultValue) {
        String value = queryParams.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get query parameter as long
     *
     * @param key
     * @param defaultValue
     * @return Long value of the query parameter or the default value if not found or invalid
     */
    public Long getQueryParamAsLong(String key, Long defaultValue) {
        String value = queryParams.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void parseHeaders(BufferedReader reader) throws IOException {
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break; // End of headers (empty line)
            }

            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
    }

    /**
     * Set an attribute in the request
     *
     * @param key   The class type of the attribute
     * @param value The value to set
     */

    public <T> void putAttribute(Class<T> key, T value) {
        attributes.put(key, key.cast(value));
    }

    public <T> T getAttribute(Class<T> key) {
        return key.cast(attributes.get(key));
    }

    public void setPathParam(String key, String value) {
        pathParams.put(key, value);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setPath(String path) {
        this.path = path;
    }


    /**
     * Returns the client's IP address, taking into account forwarded headers for proxy setups.
     * This method follows a strategy similar to Express.js and Spring Boot by checking multiple
     * headers in the following order:
     * 1. X-Forwarded-For
     * 2. Proxy-Client-IP
     * 3. WL-Proxy-Client-IP
     * 4. HTTP_X_FORWARDED_FOR
     * 5. HTTP_X_FORWARDED
     * 6. HTTP_X_CLUSTER_CLIENT_IP
     * 7. HTTP_CLIENT_IP
     * 8. HTTP_FORWARDED_FOR
     * 9. HTTP_FORWARDED
     * 10. HTTP_VIA
     * 11. REMOTE_ADDR
     * <p>
     * If an IP is found in any of these headers, it returns the first non-internal IP address.
     * If no valid IP is found in headers, it returns the socket's remote address.
     *
     * @return The client's IP address as a string
     */
    public String getIpAddress() {
        if (remoteIpAddress != null) {
            return remoteIpAddress;
        }

        // Check headers in a specific order (similar to Express.js and Spring Boot)
        String[] headerNames = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String headerName : headerNames) {
            String header = getHeader(headerName);
            if (header != null && !header.isEmpty() && !"unknown".equalsIgnoreCase(header)) {
                // X-Forwarded-For may contain multiple IPs; the first one is the client
                if (headerName.equalsIgnoreCase("X-Forwarded-For")) {
                    String[] ips = header.split(",");
                    for (String ip : ips) {
                        String trimmedIp = ip.trim();
                        if (isValidIpAddress(trimmedIp) && !isInternalIpAddress(trimmedIp)) {
                            remoteIpAddress = trimmedIp;
                            return remoteIpAddress;
                        }
                    }
                } else {
                    String trimmedIp = header.trim();
                    if (isValidIpAddress(trimmedIp)) {
                        remoteIpAddress = trimmedIp;
                        return remoteIpAddress;
                    }
                }
            }
        }

        // If no IP found in headers, use the remote address (to be set by the server implementation)
        // You should set this value when accepting the socket connection
        if (remoteIpAddress == null) {
            remoteIpAddress = "0.0.0.0"; // Default fallback
        }

        return remoteIpAddress;
    }

    /**
     * Sets the remote IP address from the socket connection.
     * This should be called by the server when accepting the connection.
     *
     * @param ipAddress The remote IP address from the socket
     */
    public void setRemoteIpAddress(String ipAddress) {
        if (this.remoteIpAddress == null) {
            this.remoteIpAddress = ipAddress;
        }
    }

    /**
     * Checks if an IP address is valid (either IPv4 or IPv6)
     *
     * @param ip The IP address to validate
     * @return true if the IP is valid, false otherwise
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // Simple regex for IPv4
        if (ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) {
            return true;
        }

        // Simple check for IPv6 - this is a basic check and may need to be enhanced
        if (ip.contains(":") && ip.matches("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::$|^::1$|^([0-9a-fA-F]{1,4}::?){1,7}([0-9a-fA-F]{1,4})?$")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if an IP address is an internal/private network address
     *
     * @param ip The IP address to check
     * @return true if the IP is internal, false otherwise
     */
    private boolean isInternalIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }

        // Check for loopback addresses
        if (ip.equals("127.0.0.1") || ip.equals("::1")) {
            return true;
        }

        // Check for private IPv4 address ranges
        if (ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+")) {
            return true;
        }

        // Check for private IPv6 addresses
        if (ip.startsWith("fc") || ip.startsWith("fd")) {
            return true;
        }

        return false;
    }
}