package com.cabin.express.router;

import com.cabin.express.http.CabinRequest;
import com.cabin.express.http.CabinResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class Router {
    private final Map<String, Map<String, BiConsumer<CabinRequest, CabinResponse>>> routes = new HashMap<>();

    public Router getInstance () {
        return new Router();
    }

    public Router() {
        // Initialize route maps for each HTTP method
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}) {
            routes.put(method, new HashMap<>());
        }
    }

    public void addRoute(String method, String path, BiConsumer<CabinRequest, CabinResponse> handler) {
        Map<String, BiConsumer<CabinRequest, CabinResponse>> methodRoutes = routes.get(method.toUpperCase());
        if (methodRoutes != null) {
            methodRoutes.put(path, handler);
        }
    }

    public BiConsumer<CabinRequest, CabinResponse> match(String method, String path) {
        Map<String, BiConsumer<CabinRequest, CabinResponse>> methodRoutes = routes.get(method.toUpperCase());
        if (methodRoutes != null) {
            return methodRoutes.get(path);
        }
        return null;
    }
}
