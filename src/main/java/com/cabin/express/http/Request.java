package com.cabin.express.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
    private Map<String, UploadedFile> uploadedFiles = new HashMap<>();

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
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1));

            // **1️⃣ Read & Parse the Request Line**
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                throw new IllegalArgumentException("Invalid HTTP request: Missing request line");
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                throw new IllegalArgumentException("Malformed request line: " + requestLine);
            }

            method = requestParts[0];
            String fullPath = requestParts[1];
            parsePathAndQuery(fullPath);

            // **2️⃣ Read & Parse Headers**
            parseHeaders(reader);

            System.err.println("Headers: " + headers); // Debugging output

            // **3️⃣ Read & Parse Body (if any)**
            if (headers.containsKey("content-length")) {
                int contentLength = Integer.parseInt(headers.get("content-length"));
                byte[] bodyBytes = new byte[contentLength];

                // Read the exact content-length bytes from the stream
                int bytesRead = inputStream.read(bodyBytes);
                if (bytesRead != contentLength) {
                    throw new IOException("Unexpected end of request body");
                }

                // **4️⃣ Process Body Based on Content-Type**
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

        } catch (Exception ex) {
            throw new Exception("Failed to parse request: " + ex.getMessage(), ex);
        }
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
    public UploadedFile getUploadedFile(String fieldName) {
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

