package com.cabin.express.http;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP request.
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

    private static final Gson gson = new Gson();


    public Request(InputStream inputStream) throws Exception {
        parseRequest(inputStream);
        parseBodyAsJson();
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
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String requestLine = reader.readLine();

        if (requestLine == null || requestLine.isEmpty()) {
            throw new IllegalArgumentException("Malformed HTTP request: Missing request line");
        }

        String[] requestParts = requestLine.split(" ");
        method = requestParts[0];
        String fullPath = requestParts[1];
        parsePathAndQuery(fullPath);

        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            String[] headerParts = headerLine.split(": ", 2);
            headers.put(headerParts[0], headerParts[1]);
        }

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            String contentLength = headers.get("Content-Length");
            if (contentLength != null) {
                int length = Integer.parseInt(contentLength);
                char[] bodyChars = new char[length];
                reader.read(bodyChars);
                body = new String(bodyChars);
            }
        }
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

