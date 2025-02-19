package com.cabin.express.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Represents an HTTP request.
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */
public class Request {
    private String method;
    private String path;
    private String body;
    private Map<String, Object> bodyAsJson = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private Map<String, String> pathParams = new HashMap<>();
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> formFields = new HashMap<>();
    private Map<String, List<UploadedFile>> uploadedFiles = new HashMap<>();

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

            headers = new HashMap<>();
            String headerLine;
            while (!(headerLine = readLine(inputStream)).isEmpty()) {  // End of headers marked by an empty line
                int colonIndex = headerLine.indexOf(":");
                if (colonIndex > 0) {
                    String key = headerLine.substring(0, colonIndex).trim().toLowerCase();
                    String value = headerLine.substring(colonIndex + 1).trim();
                    headers.put(key, value);
                }
            }

            int contentLength = 0;
            if (headers.containsKey("content-length")) {
                contentLength = Integer.parseInt(headers.get("content-length"));
            }

            if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int totalBytesRead = 0;

                while (totalBytesRead < contentLength) {
                    int bytesRead = inputStream.read(bodyBytes, totalBytesRead, contentLength - totalBytesRead);
                    if (bytesRead == -1) {
                        break; // End of stream
                    }
                    totalBytesRead += bytesRead;
                }

                if (totalBytesRead < contentLength) {
                    System.err.println("Expected " + contentLength + " bytes, but read " + totalBytesRead + " bytes");
                    throw new IOException("Unexpected end of request body");
                }

                // **4. Handle Different Content Types**
                if (headers.containsKey("content-type")) {
                    String contentType = headers.get("content-type");
                    if (contentType.contains("application/x-www-form-urlencoded")) {
                        body = new String(bodyBytes, StandardCharsets.UTF_8);
                        parseFormUrlEncodedBody();
                    } else if (contentType.contains("multipart/form-data")) {
                        parseMultipartBody(bodyBytes, contentType);
                    } else if (contentType.contains("application/json")) {
                        body = new String(bodyBytes, StandardCharsets.UTF_8);
                        parseJsonBody();
                    } else {
                        body = new String(bodyBytes, StandardCharsets.UTF_8);
                    }
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
     * Parses the request body as an object of the specified class.
     *
     * @param <T>   The type of the object to parse the body as.
     * @param clazz The class of the object to parse the body as.
     * @return The parsed object, or null if the body is null or not JSON.
     * @throws IllegalArgumentException if the body cannot be parsed as the specified class.
     */
    public <T> T getBodyAs(Class<T> clazz) {
        if (body != null && headers.containsKey("Content-Type") && headers.get("Content-Type").toLowerCase().contains("application/json")) {
            try {
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
}

