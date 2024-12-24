package com.cabin.express.router;

import com.cabin.express.http.CabinRequest;
import com.cabin.express.http.CabinResponse;
import com.cabin.express.inter.CabinHandler;

import java.util.HashMap;
import java.util.Map;

public class CabinRouter {
    private final Map<String, Map<String, CabinHandler>> routes = new HashMap<>();

    private void addRoute(String method, String path, CabinHandler handler) {
        method = method.toUpperCase();
        routes.putIfAbsent(method, new HashMap<>());
        routes.get(method).put(path, handler);
    }

    public void get(String path, CabinHandler handler) {
        addRoute("GET", path, handler);
    }

    public void post(String path, CabinHandler handler) {
        addRoute("POST", path, handler);
    }

    public void put(String path, CabinHandler handler) {
        addRoute("PUT", path, handler);
    }

    public void delete(String path, CabinHandler handler) {
        addRoute("DELETE", path, handler);
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
}
