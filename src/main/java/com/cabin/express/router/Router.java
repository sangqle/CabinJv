package com.cabin.express.router;

import com.cabin.express.CabinLogger;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.MiddlewareChain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Router {
    private final Map<String, Map<String, Handler>> routes = new HashMap<>();
    private final List<Middleware> middlewares = new ArrayList<>();

    private void addRoute(String method, String path, Handler handler) {
        method = method.toUpperCase();
        routes.putIfAbsent(method, new HashMap<>());
        routes.get(method).put(path, handler);
    }

    public void get(String path, Handler handler) {
        addRoute("GET", path, handler);
    }

    public void post(String path, Handler handler) {
        addRoute("POST", path, handler);
    }

    public void put(String path, Handler handler) {
        addRoute("PUT", path, handler);
    }

    public void delete(String path, Handler handler) {
        addRoute("DELETE", path, handler);
    }

    // Attempt to route the request
//    public boolean routeRequest(Request request, Response response) {
//        String method = request.getMethod();
//        String path = request.getPath();
//
//        Handler handler = routes.getOrDefault(method, new HashMap<>()).get(path);
//
//        if (handler != null) {
//            try {
//                handler.handle(request, response);
//                response.send();
//                return true; // Indicates the request was handled
//            } catch (Exception e) {
//                CabinLogger.error("Error in route handler: " + e.getMessage(), e);
//                response.setStatusCode(500);
//                response.writeBody("Internal Server Error");
//                try {
//                    response.send();
//                } catch (Exception ex) {
//                    CabinLogger.error("Error sending error response: " + ex.getMessage(), ex);
//                }
//            }
//        }
//        return false; // Indicates the request was not handled
//    }


    // Handle an incoming request, applying middleware and routing
    public boolean handleRequest(Request request, Response response) {
        // Wrap route handling in a middleware chain
        AtomicBoolean handled = new AtomicBoolean(false);

        String method = request.getMethod();
        String path = request.getPath();
        Handler handler = routes.getOrDefault(method, new HashMap<>()).get(path);

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
}
