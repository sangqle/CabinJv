package com.cabin.express.http;

import com.google.gson.Gson;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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


    public Request(InputStream inputStream) throws Exception {
        parseRequest(inputStream);
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


    private void parseRequest(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1));

        // Read request line
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IllegalArgumentException("Malformed HTTP request: Missing request line");
        }

        String[] requestParts = requestLine.split(" ");
        if (requestParts.length < 2) {
            throw new IllegalArgumentException("Malformed HTTP request: Incomplete request line");
        }

        method = requestParts[0];
        path = requestParts[1];

        // Read headers
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            String[] headerParts = headerLine.split(": ", 2);
            if (headerParts.length == 2) {
                headers.put(headerParts[0].trim(), headerParts[1].trim());
            }
        }

        // Handle body if present
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            String contentType = headers.getOrDefault("Content-Type", "").toLowerCase();
            if (contentType.startsWith("multipart/form-data")) {
                parseMultipart(inputStream, contentType);
            } else {
                parseBody(reader);
            }
        }
    }


    private void parseBody(BufferedReader reader) throws IOException {
        String contentLengthHeader = headers.get("Content-Length");
        if (contentLengthHeader != null) {
            try {
                int contentLength = Integer.parseInt(contentLengthHeader);
                char[] bodyChars = new char[contentLength];
                reader.read(bodyChars);
                body = new String(bodyChars);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid Content-Length header");
            }
        }
    }

    private void parseMultipart(InputStream inputStream, String contentType) throws IOException {
        String boundary = "--" + contentType.split("boundary=")[1];

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1));
        String line;
        boolean inPart = false;
        String fieldName = null;
        String fileName = null;
        String contentTypePart = null;
        ByteArrayOutputStream fileBuffer = new ByteArrayOutputStream();

        while ((line = reader.readLine()) != null) {
            if (line.startsWith(boundary)) {
                // End of previous part
                if (inPart && fileName != null) {
                    // Store the file
                    uploadedFiles.put(fieldName, new UploadedFile(fileName, contentTypePart, fileBuffer.toByteArray()));
                    fileBuffer.reset();
                }
                inPart = false;
                fieldName = null;
                fileName = null;
                contentTypePart = null;
            } else if (line.startsWith("Content-Disposition:")) {
                String[] parts = line.split(";");
                for (String part : parts) {
                    if (part.trim().startsWith("name=")) {
                        fieldName = part.split("=")[1].replace("\"", "");
                    } else if (part.trim().startsWith("filename=")) {
                        fileName = part.split("=")[1].replace("\"", "");
                    }
                }
            } else if (line.startsWith("Content-Type:")) {
                contentTypePart = line.split(": ")[1];
            } else if (line.isEmpty() && fileName != null) {
                // Start of file content
                inPart = true;
            } else if (inPart) {
                // Write file content to buffer
                fileBuffer.write(line.getBytes(StandardCharsets.ISO_8859_1));
                fileBuffer.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            }
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

}

