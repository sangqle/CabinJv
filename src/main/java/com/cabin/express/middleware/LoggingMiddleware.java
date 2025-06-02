package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.loggger.CabinLogger.LoggerInstance;

import java.io.IOException;
import java.util.UUID;

/**
 * Middleware for logging HTTP requests and responses
 */
public class LoggingMiddleware implements Middleware {
    private final LoggerInstance logger;
    private final boolean logRequestHeaders;
    private final boolean logRequestBody;
    private final boolean logResponseHeaders;
    private final boolean logResponseBody;

    /**
     * Create a new LoggingMiddleware with default settings
     */
    public LoggingMiddleware() {
        this(false, false, false, false);
    }

    /**
     * Create a new LoggingMiddleware with custom settings
     *
     * @param logRequestHeaders Whether to log request headers
     * @param logRequestBody Whether to log request body
     * @param logResponseHeaders Whether to log response headers
     * @param logResponseBody Whether to log response body
     */
    public LoggingMiddleware(
            boolean logRequestHeaders,
            boolean logRequestBody,
            boolean logResponseHeaders,
            boolean logResponseBody) {
        this.logger = CabinLogger.getLogger(LoggingMiddleware.class);
        this.logRequestHeaders = logRequestHeaders;
        this.logRequestBody = logRequestBody;
        this.logResponseHeaders = logResponseHeaders;
        this.logResponseBody = logResponseBody;
    }

    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        // Generate request ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        // Log request
        logRequest(request, requestId);

        // Proceed with request handling
        next.next(request, response);

        // Log response
        long duration = System.currentTimeMillis() - startTime;
        logResponse(request, response, requestId, duration);
    }

    private void logRequest(Request request, String requestId) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] --> %s %s", requestId, request.getMethod(), request.getPath()));

        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            sb.append("?").append(request.getQueryString());
        }

        logger.info(sb.toString());

        if (logRequestHeaders && request.getHeaders() != null) {
            logger.debug(String.format("[%s] Request Headers: %s", requestId, request.getHeaders()));
        }

        if (logRequestBody) {
            try {
                String body = request.getBodyAsString();
                if (body != null && !body.isEmpty()) {
                    // Truncate very long bodies
                    if (body.length() > 1000) {
                        body = body.substring(0, 997) + "...";
                    }
                    logger.debug(String.format("[%s] Request Body: %s", requestId, body));
                }
            } catch (Exception e) {
                logger.warn(String.format("[%s] Could not log request body: %s", requestId, e.getMessage()));
            }
        }
    }

    private void logResponse(Request request, Response response, String requestId, long duration) {
        logger.info(String.format("[%s] <-- %s %s %d (%dms)",
                requestId, request.getMethod(), request.getPath(), response.getStatusCode(), duration));

        if (logResponseHeaders && response.getHeaders() != null) {
            logger.debug(String.format("[%s] Response Headers: %s", requestId, response.getHeaders()));
        }
    }
}