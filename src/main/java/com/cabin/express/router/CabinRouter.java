package com.cabin.express.router;

import com.cabin.express.CabinJLogger;
import com.cabin.express.http.CabinRequest;
import com.cabin.express.http.CabinResponse;
import com.cabin.express.inter.CabinHandler;

import java.util.HashMap;
import java.util.Map;

public class CabinRouter {
    private final Map<String, Map<String, CabinHandler>> routes = new HashMap<>();

    public void addRoute(String method, String path, CabinHandler handler) {
        method = method.toUpperCase();
        routes.putIfAbsent(method, new HashMap<>());
        routes.get(method).put(path, handler);
    }

    public void handle(CabinRequest request, CabinResponse response) throws Exception {
        String method = request.getMethod().toUpperCase();
        String path = request.getPath();

        CabinHandler handler = routes.getOrDefault(method, new HashMap<>()).get(path);

        if (handler != null) {
            handler.handle(request, response);
        } else {
            response.setStatusCode(404);
            response.writeBody("Not Found");
            response.send();
        }
    }

    // Helper method to send error responses
    private void sendErrorResponse(CabinResponse response, int statusCode, String message) {
        response.setStatusCode(statusCode);
        response.setHeader("Content-Type", "text/plain");
        response.writeBody(message);
        try {
            response.send();
        } catch (Exception e) {
            CabinJLogger.error("Error sending error response", e);
        }
    }

    public Map<String, Map<String, CabinHandler>> getRoutes() {
        return routes;
    }
}
