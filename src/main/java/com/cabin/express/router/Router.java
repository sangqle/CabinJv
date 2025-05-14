package com.cabin.express.router;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.MiddlewareChain;
import com.cabin.express.loggger.CabinLogger;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Router implements Middleware {
    private final String name = "Router";
    private String prefix = "";
    private final Map<String, List<Route>> routes;
    private final List<Middleware> middlewares;

    public Router() {
        this.routes = new HashMap<>();
        this.middlewares = new ArrayList<>();
    }

    // Route class to store route information
    private static class Route {
        final Pattern pattern;
        final Handler handler;
        final List<Middleware> middlewares;

        Route(Pattern pattern, Handler handler) {
            this.pattern = pattern;
            this.handler = handler;
            this.middlewares = new ArrayList<>();
        }
    }

    // Support for all HTTP methods
    public Router all(String path, Handler handler) {
        addRoute("ALL", path, handler);
        return this;
    }

    public Router get(String path, Handler handler) {
        addRoute("GET", path, handler);
        return this;
    }

    public Router post(String path, Handler handler) {
        addRoute("POST", path, handler);
        return this;
    }

    public Router put(String path, Handler handler) {
        addRoute("PUT", path, handler);
        return this;
    }

    public Router delete(String path, Handler handler) {
        addRoute("DELETE", path, handler);
        return this;
    }

    public Router patch(String path, Handler handler) {
        addRoute("PATCH", path, handler);
        return this;
    }

    public Router options(String path, Handler handler) {
        addRoute("OPTIONS", path, handler);
        return this;
    }

    // Middleware support
    public Router use(Middleware middleware) {
        middlewares.add(middleware);
        return this;
    }

    // Route-specific middleware
    public Router use(String path, Middleware... middlewares) {
        for (String method : routes.keySet()) {
            List<Route> methodRoutes = routes.get(method);
            for (Route route : methodRoutes) {
                if (route.pattern.pattern().equals(pathToPattern(path))) {
                    Collections.addAll(route.middlewares, middlewares);
                }
            }
        }
        return this;
    }

    private void addRoute(String method, String path, Handler handler) {
        String fullPath = prefix.isEmpty() ? path : prefix + path;
        Pattern pattern = Pattern.compile(pathToPattern(fullPath));
        routes.computeIfAbsent(method, k -> new ArrayList<>())
                .add(new Route(pattern, handler));
    }

    private String pathToPattern(String path) {
        // Convert Express-style path params (:param) to regex
        // Also handle optional parameters and wildcards
        return "^" + path
                .replaceAll(":([^/]+)\\?", "(?<$1>[^/]*)?")
                .replaceAll(":([^/]+)", "(?<$1>[^/]+)")
                .replaceAll("\\*", ".*")
                + "$";
    }

    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        String method = request.getMethod();
        String path = request.getPath();

        // Try method-specific routes
        if (tryRoute(method, path, request, response)) {
            return;
        }

        // Try ALL routes
        if (tryRoute("ALL", path, request, response)) {
            return;
        }

        // No matching route found, pass to next middleware
        next.next(request, response);
    }

    private boolean tryRoute(String method, String path, Request request, Response response) {
        List<Route> methodRoutes = routes.getOrDefault(method, Collections.emptyList());

        for (Route route : methodRoutes) {
            Matcher matcher = route.pattern.matcher(path);
            if (matcher.matches()) {
                // Extract path parameters
                Map<String, String> params = extractPathParams(matcher);
                request.setPathParams(params);

                // Create middleware chain including route-specific middlewares
                List<Middleware> routeChain = new ArrayList<>(middlewares);
                routeChain.addAll(route.middlewares);
                routeChain.add((req, res, chain) -> {
                    try {
                        route.handler.handle(req, res);
                    } catch (Exception e) {
                        CabinLogger.error("Error in route handler: " + e.getMessage(), e);
                        res.setStatusCode(500);
                        res.writeBody("Internal Server Error");
                        res.send();
                    }
                });

                try {
                    new MiddlewareChain(routeChain, (req, res) -> {
                        CabinLogger.warn("No handler found for route: " + req.getPath());
                        res.setStatusCode(404);
                        res.writeBody("Not Found");
                        res.send();
                    }).next(request, response);

                    return true;
                } catch (Exception e) {
                    CabinLogger.error("Error processing request: " + e.getMessage(), e);
                    try {
                        response.setStatusCode(500);
                        response.writeBody("Internal Server Error");
                        response.send();
                    } catch (Exception ex) {
                        CabinLogger.error("Error sending error response: " + ex.getMessage(), ex);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, String> extractPathParams(Matcher matcher) {
        Map<String, String> params = new HashMap<>();
        for (String groupName : getNamedGroups(matcher.pattern())) {
            String value = matcher.group(groupName);
            if (value != null) {
                params.put(groupName, value);
            }
        }
        return params;
    }

    private Set<String> getNamedGroups(Pattern pattern) {
        Set<String> groups = new HashSet<>();
        String patternString = pattern.pattern();
        Matcher m = Pattern.compile("\\(\\?<([^>]+)>").matcher(patternString);
        while (m.find()) {
            groups.add(m.group(1));
        }
        return groups;
    }

    public void setPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        this.prefix = "/" + prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    public String getPrefix() {
        return prefix;
    }

    public String getName() {
        return name;
    }

    public Map<String, List<Route>> getRoutes() {
        return routes;
    }

    public List<Middleware> getMiddlewares() {
        return middlewares;
    }

    public HashSet<String> getEndpoint() {
        HashSet<String> endpoints = new HashSet<>();
        for (String method : routes.keySet()) {
            List<Route> methodRoutes = routes.get(method);
            for (Route route : methodRoutes) {
                endpoints.add(method + " " + route.pattern.pattern());
            }
        }
        return endpoints;
    }

}