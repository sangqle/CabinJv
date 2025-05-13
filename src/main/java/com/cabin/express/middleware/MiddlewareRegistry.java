package com.cabin.express.middleware;

import com.cabin.express.interfaces.Middleware;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for tracking middleware added to the server.
 * This helps identify when specific middleware types are present
 * in the processing chain.
 */
public class MiddlewareRegistry {
    private static final List<Class<? extends Middleware>> registeredMiddlewares = new ArrayList<>();
    private static boolean hasGzipMiddleware = false;

    /**
     * Registers a middleware in the registry.
     *
     * @param middleware the middleware to register
     */
    public static void register(Middleware middleware) {
        registeredMiddlewares.add(middleware.getClass());

        // Check if this is GzipMiddleware or a subclass
        if (middleware instanceof GzipMiddleware ||
                GzipMiddleware.class.isAssignableFrom(middleware.getClass())) {
            hasGzipMiddleware = true;
        }
    }

    /**
     * Checks if a specific middleware type is registered.
     *
     * @param middlewareClass the class of middleware to check for
     * @return true if the middleware is registered, false otherwise
     */
    public static boolean hasMiddleware(Class<? extends Middleware> middlewareClass) {
        return registeredMiddlewares.contains(middlewareClass) ||
                registeredMiddlewares.stream().anyMatch(middlewareClass::isAssignableFrom);
    }

    /**
     * Checks if GzipMiddleware is in the registry.
     *
     * @return true if GzipMiddleware is registered, false otherwise
     */
    public static boolean hasGzipMiddleware() {
        return hasGzipMiddleware;
    }

    /**
     * Clears the registry.
     */
    public static void clear() {
        registeredMiddlewares.clear();
        hasGzipMiddleware = false;
    }
}