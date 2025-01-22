package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a middleware chain for an HTTP request.
 * <p>
 * The MiddlewareChain class is used to apply a list of middleware to an HTTP request. It processes each middleware
 * in the chain in order, passing the request and response objects to each middleware. The MiddlewareChain class
 * also provides a way to call the final route handler after all middleware have been applied.
 * <p>
 *
 * @version 1.0.0
 * @since 2024-12-24
 * @author Sang Le
 */
public class MiddlewareChain {
    private final Iterator<Middleware> middlewareIterator;
    private final Handler routeHandler;

    /**
     * Create a new middleware chain
     *
     * @param middleware   the list of middleware to apply
     * @param routeHandler the final route handler
     */
    public MiddlewareChain(List<Middleware> middleware, Handler routeHandler) {
        this.middlewareIterator = middleware.iterator();
        this.routeHandler = routeHandler;
    }

    /**
     * Processes the next middleware in the chain or the final route handler if no middleware is left.
     *
     * @param request  the request object
     * @param response the response object
     * @throws IOException if an I/O error occurs during request processing
     */
    public void next(Request request, Response response) throws IOException {
        if (middlewareIterator.hasNext()) {
            Middleware current = middlewareIterator.next();
            current.apply(request, response, this);
        } else if (routeHandler != null) {
            routeHandler.handle(request, response);
        }
    }
}
