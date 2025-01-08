package com.cabin.express.router;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.MiddlewareChain;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * The Router class is used to define routes and handle requests. It is used to define routes for different HTTP
 * methods and handle requests based on the method and path of the request. The Router class also supports
 * middleware, which can be used to perform operations before or after handling a request.
 * <p>
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 */
public class Router {

    private static final String name = "Router";
    private String prefix = "";
    private final Map<String, Map<Pattern, Handler>> methodRoutes = new HashMap<>();
    private final List<Middleware> middlewares = new ArrayList<>();

    private void addRoute(String method, String path, Handler handler) {
        method = method.toUpperCase();
        methodRoutes.putIfAbsent(method, new HashMap<>());
        String regexPath = path.replaceAll(":(\\w+)", "(?<$1>[^/]+)");
        Pattern pattern = Pattern.compile("^" + regexPath + "$");
        methodRoutes.get(method).put(pattern, handler);
    }

    /**
     * Define a route for the GET method
     *
     * @param path    the path of the route
     * @param handler the handler for the route
     */
    public void get(String path, Handler handler) {
        if (!Objects.equals(prefix, "")) {
            path = prefix + path;
        }
        addRoute("GET", path, handler);
    }

    /**
     * Define a route for the POST method
     *
     * @param path    the path of the route
     * @param handler the handler for the route
     */
    public void post(String path, Handler handler) {
        if (!Objects.equals(prefix, "")) {
            path = prefix + path;
        }
        addRoute("POST", path, handler);
    }

    /**
     * Define a route for the PUT method
     *
     * @param path    the path of the route
     * @param handler the handler for the route
     */
    public void put(String path, Handler handler) {
        if (!Objects.equals(prefix, "")) {
            path = prefix + path;
        }
        addRoute("PUT", path, handler);
    }

    /**
     * Define a route for the DELETE method
     *
     * @param path    the path of the route
     * @param handler the handler for the route
     */
    public void delete(String path, Handler handler) {
        if (!Objects.equals(prefix, "")) {
            path = prefix + path;
        }
        addRoute("DELETE", path, handler);
    }

    /**
     * Handle a request
     *
     * @param request  the request object
     * @param response the response object
     * @return true if the request was handled, false otherwise
     */
    public boolean handleRequest(Request request, Response response) {
        AtomicBoolean handled = new AtomicBoolean(false);

        String method = request.getMethod();
        String path = request.getPath();
        Map<Pattern, Handler> routes = methodRoutes.getOrDefault(method, new HashMap<>());

        for (Map.Entry<Pattern, Handler> entry : routes.entrySet()) {
            Matcher matcher = entry.getKey().matcher(path);
            if (matcher.matches()) {
                request.setPathParams(extractPathParams(matcher));
                request.setQueryParams(parseQueryParams(request.getQueryString()));

                MiddlewareChain chain = new MiddlewareChain(middlewares, (req, res) -> {
                    try {
                        entry.getValue().handle(req, res);
                        handled.set(true);
                    } catch (Exception e) {
                        CabinLogger.error("Error in route handler: " + e.getMessage(), e);
                        res.setStatusCode(500);
                        res.writeBody("Internal Server Error");
                    }
                });

                try {
                    chain.next(request, response);
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
        }

        return false;
    }

    /**
     * Add a middleware to this router
     *
     * @param middleware the middleware to add
     */
    public void use(Middleware middleware) {
        middlewares.add(middleware);
    }

    /**
     * Sets the prefix for all routes in this router.
     * <p>
     * The prefix is prepended to all routes in this router. For example, if the prefix is "/api/v1",
     *
     * @param prefix the new prefix to set for all routes. If null or empty, the method does nothing.
     */
    public void setPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        prefix = "/" + prefix.replaceAll("^/+", "").replaceAll("/+$", "");

        Map<String, Map<Pattern, Handler>> newMethodRoutes = new HashMap<>();
        for (Map.Entry<String, Map<Pattern, Handler>> entry : methodRoutes.entrySet()) {
            Map<Pattern, Handler> newRoutes = new HashMap<>();
            for (Map.Entry<Pattern, Handler> route : entry.getValue().entrySet()) {
                String path = route.getKey().pattern();
                path = path.replaceFirst("^" + this.prefix, "");
                path = prefix + path;
                newRoutes.put(Pattern.compile(path), route.getValue());
            }
            newMethodRoutes.put(entry.getKey(), newRoutes);
        }
        methodRoutes.clear();
        methodRoutes.putAll(newMethodRoutes);
        this.prefix = prefix;
    }

    /**
     * Get the prefix for all routes in this router
     *
     * @return the prefix for all routes in this router
     */
    public Set<String> getEndpoint() {
        Set<String> endpoints = new HashSet<>();
        for (Map.Entry<String, Map<Pattern, Handler>> entry : methodRoutes.entrySet()) {
            for (Pattern pattern : entry.getValue().keySet()) {
                endpoints.add(entry.getKey() + "-" + pattern.pattern());
            }
        }
        return endpoints;
    }

    private Map<String, String> parseQueryParams(String queryString) {
        Map<String, String> queryParams = new HashMap<>();
        if (queryString != null && !queryString.isEmpty()) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length > 1) {
                    queryParams.put(keyValue[0], keyValue[1]);
                } else {
                    queryParams.put(keyValue[0], "");
                }
            }
        }
        return queryParams;
    }

    private Map<String, String> extractPathParams(Matcher matcher) {
        Map<String, String> pathParams = new HashMap<>();
        Pattern pattern = matcher.pattern();
        String[] groupNames = pattern.pattern().split("\\(\\?<");
        for (int i = 1; i < groupNames.length; i++) {
            String groupName = groupNames[i].split(">")[0];
            pathParams.put(groupName, matcher.group(groupName));
        }
        return pathParams;
    }
}
