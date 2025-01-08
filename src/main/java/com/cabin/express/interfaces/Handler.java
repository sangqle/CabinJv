package com.cabin.express.interfaces;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;

import java.io.IOException;

/**
 * Represents a handler for an HTTP request.
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */
@FunctionalInterface
public interface Handler {
    /**
     * Handles an HTTP request.
     *
     * @param request  the request object
     * @param response the response object
     * @throws IOException if an I/O error occurs during request processing
     */
    void handle(Request request, Response response) throws IOException;
}