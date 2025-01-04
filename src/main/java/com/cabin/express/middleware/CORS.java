package com.cabin.express.middleware;


import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;

import java.io.IOException;
import java.util.List;

public class CORS implements Middleware {
    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;
    private final boolean allowCredentials;

    public CORS(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders, boolean allowCredentials) {
        this.allowedOrigins = allowedOrigins;
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.allowCredentials = allowCredentials;
    }

    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && (allowedOrigins.contains("*") || allowedOrigins.contains(origin))) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Methods", String.join(", ", allowedMethods));
            response.setHeader("Access-Control-Allow-Headers", String.join(", ", allowedHeaders));
            if (allowCredentials) {
                response.setHeader("Access-Control-Allow-Credentials", "true");
            }
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatusCode(204); // No Content
            response.send();
        } else {
            next.next(request, response);
        }
    }
}
