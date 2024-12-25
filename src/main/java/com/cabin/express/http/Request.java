package com.cabin.express.http;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Request {
    private String method;
    private String path;
    private String body;
    private Map<String, Object> bodyAsJson = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private Map<String, String> headers = new HashMap<>();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Request(InputStream inputStream) throws Exception {
        parseRequest(inputStream);
        parseBodyAsJson();
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
        if (body != null && headers.containsKey("Content-Type")
                && headers.get("Content-Type").toLowerCase().contains("application/json")) {
            try {
                bodyAsJson = objectMapper.readValue(body, Map.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse JSON body: " + e.getMessage(), e);
            }
        }
    }


    public Map<String, Object> getBody() {
        return bodyAsJson;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getQueryParam(String key) {
        return queryParams.get(key);
    }

    public String getHeader(String key) {
        return headers.get(key);
    }
}
