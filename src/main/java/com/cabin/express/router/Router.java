package com.cabin.express.router;

import com.cabin.express.CabinLogger;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.inter.Handler;

import java.util.HashMap;
import java.util.Map;

public class Router {
    private final Map<String, Map<String, Handler>> routes = new HashMap<>();

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

    @Deprecated
    private void handle(Request request, Response response) throws Exception {
        String method = request.getMethod().toUpperCase();
        String path = request.getPath();

        Handler handler = routes.getOrDefault(method, new HashMap<>()).get(path);

        if (handler != null) {
            handler.handle(request, response);
        } else {
            response.setStatusCode(404);
            response.writeBody("Not Found");
            response.send();
        }
    }

    // Attempt to route the request
    public boolean routeRequest(Request request, Response response) {
        String method = request.getMethod();
        String path = request.getPath();

        Handler handler = routes.getOrDefault(method, new HashMap<>()).get(path);

        if (handler != null) {
            try {
                handler.handle(request, response);
                response.send();
                return true; // Indicates the request was handled
            } catch (Exception e) {
                CabinLogger.error("Error in route handler: " + e.getMessage(), e);
                response.setStatusCode(500);
                response.writeBody("Internal Server Error");
                try {
                    response.send();
                } catch (Exception ex) {
                    CabinLogger.error("Error sending error response: " + ex.getMessage(), ex);
                }
            }
        }
        return false; // Indicates the request was not handled
    }
}
