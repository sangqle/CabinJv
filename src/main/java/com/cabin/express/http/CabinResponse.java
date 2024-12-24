package com.cabin.express.http;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class CabinResponse {
    private int statusCode = 200;
    private Map<String, String> headers = new HashMap<>();
    private StringBuilder body = new StringBuilder();
    private final OutputStream outputStream;

    public CabinResponse(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public void writeBody(String content) {
        body.append(content);
    }

    public void sendResponse() throws Exception {
        PrintWriter writer = new PrintWriter(outputStream);
        writer.printf("HTTP/1.1 %d OK\r\n", statusCode);
        headers.forEach((key, value) -> writer.printf("%s: %s\r\n", key, value));
        writer.printf("Content-Length: %d\r\n", body.length());
        writer.print("\r\n");
        writer.print(body.toString());
        writer.flush();
    }
}
