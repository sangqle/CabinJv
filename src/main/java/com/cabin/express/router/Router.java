package com.cabin.express.router;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.MiddlewareChain;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Router {

    private static final String name = "Router";
    private String prefix = "";
    private final Map<String, Map<String, Handler>> methodRoutes = new HashMap<>();
    private final List<Middleware> middlewares = new ArrayList<>();

    private void addRoute(String method, String path, Handler handler) {
        method = method.toUpperCase();
        methodRoutes.putIfAbsent(method, new HashMap<>());
        methodRoutes.get(method).put(path, handler);
    }

    public void get(String path, Handler handler) {
        if (prefix != "") {
            path = prefix + path;
        }
        addRoute("GET", path, handler);
    }

    public void post(String path, Handler handler) {
        if (prefix != "") {
            path = prefix + path;
        }
        addRoute("POST", path, handler);
    }

    public void put(String path, Handler handler) {
        if (prefix != "") {
            path = prefix + path;
        }
        addRoute("PUT", path, handler);
    }

    public void delete(String path, Handler handler) {
        if (prefix != "") {
            path = prefix + path;
        }
        addRoute("DELETE", path, handler);
    }

    // Handle an incoming request, applying middleware and routing
    public boolean handleRequest(Request request, Response response) {
        // Wrap route handling in a middleware chain
        AtomicBoolean handled = new AtomicBoolean(false);

        String method = request.getMethod();
        String path = request.getPath();
        Handler handler = methodRoutes.getOrDefault(method, new HashMap<>()).get(path);

        if (handler == null) {
            return false;
        }

        MiddlewareChain chain = new MiddlewareChain(middlewares, (req, res) -> {
            try {
                handler.handle(req, res);
                handled.set(true);
            } catch (Exception e) {
                CabinLogger.error("Error in route handler: " + e.getMessage(), e);
                res.setStatusCode(500);
                res.writeBody("Internal Server Error");
            }
        });

        try {
            chain.next(request, response); // Start the middleware chain
        } catch (Exception e) {
            CabinLogger.error("Error processing request: " + e.getMessage(), e);
            try {
                response.setStatusCode(500);
                response.writeBody("Internal Server Error");
            } catch (Exception ex) {
                CabinLogger.error("Error sending error response: " + ex.getMessage(), ex);
            }
        }

        return handled.get();
    }

    public void use(Middleware middleware) {
        middlewares.add(middleware);
    }

    public void setPrefix(String prefix) {
        // check and replace the prefix in the existing routes with the new prefix by all methods
        Map<String, Map<String, Handler>> newMethodRoutes = new HashMap<>();
        for (Map.Entry<String, Map<String, Handler>> entry : methodRoutes.entrySet()) {
            Map<String, Handler> newRoutes = new HashMap<>();
            // Endpoint path
            for (Map.Entry<String, Handler> route : entry.getValue().entrySet()) {
                String path = route.getKey();
                if(this.prefix.isEmpty()) {
                    path = prefix + path;
                } else {
                    path = path.replace(this.prefix, "");
                    path = prefix + path;
                }
                newRoutes.put(path, route.getValue());
            }
            newMethodRoutes.put(entry.getKey(), newRoutes);
        }
        methodRoutes.clear();
        methodRoutes.putAll(newMethodRoutes);
        this.prefix = prefix;
    }

    public Set<String> getEndpoint() {
        Set<String> endpoints = new HashSet<>();
        for (Map.Entry<String, Map<String, Handler>> entry : methodRoutes.entrySet()) {
            for (String path : entry.getValue().keySet()) {
                endpoints.add(entry.getKey() + "-" + path);
            }
        }
        return endpoints;
    }
}
