package com.cabin.express.router;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.MiddlewareChain;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.utils.PathUtils;

import java.io.IOException;
import java.util.*;

public class Router implements Middleware {
    private final String name = "Router";
    private String prefix = "";
    private final RouterNode root = new RouterNode();
    private final List<Middleware> globalMiddlewares = new ArrayList<>();

    // HTTP methods
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

    // Methods with middleware 
    public Router get(String path, Middleware middleware, Handler handler) {
        RouterNode node = insertPath(path);
        node.addMiddleware(middleware);
        node.addHandler("GET", handler);
        return this;
    }

    public Router get(String path, List<Middleware> middlewares, Handler handler) {
        RouterNode node = insertPath(path);
        for (Middleware middleware : middlewares) {
            node.addMiddleware(middleware);
        }
        node.addHandler("GET", handler);
        return this;
    }

    // Middleware support
    public Router use(Middleware middleware) {
        globalMiddlewares.add(middleware);
        return this;
    }

    // Mount sub-router
    public Router use(Router childRouter) {
        return use("/", childRouter);
    }

    public Router use(String path, Router childRouter) {
        // Normalize the path
        String normalizedPath = normalizePath(path);

        // Insert the path into the trie
        RouterNode node = insertPath(normalizedPath);

        // Mount the child router at this node
        node.mountRouter(normalizedPath, childRouter);

        return this;
    }

    // Add a route to the trie
    private void addRoute(String method, String path, Handler handler) {
        RouterNode node = insertPath(path);
        node.addHandler(method.toUpperCase(), handler);
    }

    // Insert a path into the trie, returning the leaf node
    private RouterNode insertPath(String path) {
        String fullPath = prefix.isEmpty() ? path : prefix + path;
        String normalizedPath = normalizePath(fullPath);
        String[] segments = normalizedPath.split("/");

        RouterNode current = root;

        for (String segment : segments) {
            if (segment.isEmpty()) continue; // Skip empty segments

            if (segment.startsWith(":")) {
                // Dynamic parameter
                String paramName = segment.substring(1);
                current = current.getOrCreateDynamicChild(paramName);
            } else if (segment.equals("*")) {
                // Wildcard
                current = current.getOrCreateWildcardChild();
                break; // Wildcard consumes the rest of the path
            } else {
                // Static path
                current = current.getOrCreateStaticChild(segment);
            }
        }

        return current;
    }

    // Helper to normalize paths
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        path = path.startsWith("/") ? path : "/" + path;
        path = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return path;
    }

    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        // First apply global middlewares
        MiddlewareChain globalChain = new MiddlewareChain(globalMiddlewares,
                (req, res) -> findAndExecuteRoute(req, res, next));
        globalChain.next(request, response);
    }

    private void findAndExecuteRoute(Request request, Response response, MiddlewareChain fallbackNext)
            throws IOException {
        String method = request.getMethod();
        String path = request.getPath();
        String normalizedPath = normalizePath(path);
        String[] segments = normalizedPath.split("/");

        // Context to collect parameters and middlewares during traversal
        Map<String, String> pathParams = new HashMap<>();
        List<Middleware> routeMiddlewares = new ArrayList<>();

        // Find matching route in the trie
        RouterNode matchedNode = findRoute(root, segments, 0, pathParams, routeMiddlewares);

        if (matchedNode != null) {
            // Check if this is a mount point for a sub-router
            if (matchedNode.isMountPoint()) {
                // Process mount point
                Router mountedRouter = matchedNode.getMountedRouter();
                String mountPrefix = matchedNode.getMountPrefix();

                // Determine the sub-path by removing the mount prefix from the path
                // This ensures the proper handling of path segments
                String subPath;
                if (path.length() > mountPrefix.length()) {
                    subPath = path.substring(mountPrefix.length());
                    if (!subPath.startsWith("/")) {
                        subPath = "/" + subPath;
                    }
                } else {
                    subPath = "/";
                }

                // Save original path for restoration later if needed
                String originalPath = request.getPath();
                Map<String, String> originalParams = new HashMap<>(pathParams);

                // Update request for child router
                request.setBaseUrl((request.getBaseUrl() + mountPrefix).replace("//+", "/"));
                request.setPath(subPath);

                // Set already extracted path parameters
                for (Map.Entry<String, String> param : pathParams.entrySet()) {
                    request.setPathParam(param.getKey(), param.getValue());
                }

                // Create a chain that will process the child router
                MiddlewareChain mountChain = new MiddlewareChain(routeMiddlewares,
                        (req, res) -> {
                            mountedRouter.apply(req, res, new MiddlewareChain(List.of(),
                                    (innerReq, innerRes) -> {
                                        // If not handled, restore original path and continue
                                        request.setPath(originalPath);
                                        fallbackNext.next(request, response);
                                    }));
                        });

                mountChain.next(request, response);
                return;
            }

            // Get handler for requested method
            Handler handler = matchedNode.getHandler(method);
            if (handler == null) {
                handler = matchedNode.getHandler("ALL"); // Try ALL if method-specific not found
            }

            if (handler != null) {
                // Set path parameters
                for (Map.Entry<String, String> param : pathParams.entrySet()) {
                    request.setPathParam(param.getKey(), param.getValue());
                }

                // Add the route handler as the final middleware
                Handler finalHandler = handler;
                routeMiddlewares.add((req, res, chain) -> {
                    try {
                        finalHandler.handle(req, res);
                    } catch (Exception e) {
                        CabinLogger.error("Error in route handler: " + e.getMessage(), e);
                        res.setStatusCode(500);
                        res.writeBody("Internal Server Error");
                        res.send();
                    }
                });

                // Execute the middleware chain
                MiddlewareChain routeChain = new MiddlewareChain(routeMiddlewares, null);
                routeChain.next(request, response);
                return;
            }
        }

        // No matching route found, pass to next middleware
        fallbackNext.next(request, response);
    }

    // Find a matching route in the trie
    private RouterNode findRoute(RouterNode node, String[] segments, int index,
                                 Map<String, String> pathParams, List<Middleware> middlewares) {
        // Add this node's middlewares
        middlewares.addAll(node.getMiddlewares());

        // Check if this is a mount point - if so, and there are more segments, return it
        if (node.isMountPoint() && index < segments.length) {
            return node;
        }

        // If we've reached the end of the path
        if (index >= segments.length) {
            return node;
        }

        String segment = segments[index];
        if (segment.isEmpty()) {
            // Skip empty segments
            return findRoute(node, segments, index + 1, pathParams, middlewares);
        }

        // Try static child (exact match)
        RouterNode staticChild = node.getStaticChild(segment);
        if (staticChild != null) {
            RouterNode result = findRoute(staticChild, segments, index + 1, pathParams, middlewares);
            if (result != null) {
                return result;
            }
        }

        // Try dynamic child (parameter)
        RouterNode dynamicChild = node.getDynamicChild();
        if (dynamicChild != null) {
            String paramName = node.getParamName();
            pathParams.put(paramName, segment);
            RouterNode result = findRoute(dynamicChild, segments, index + 1, pathParams, middlewares);
            if (result != null) {
                return result;
            }
            // Remove the param if this path doesn't lead to a match
            pathParams.remove(paramName);
        }

        // Try wildcard child (matches rest of path)
        RouterNode wildcardChild = node.getWildcardChild();
        if (wildcardChild != null) {
            // Collect all remaining segments
            StringBuilder wildcardValue = new StringBuilder(segment);
            for (int i = index + 1; i < segments.length; i++) {
                wildcardValue.append("/").append(segments[i]);
            }
            pathParams.put("wildcard", wildcardValue.toString());
            return wildcardChild;
        }

        // If we reach here, no match was found
        return null;
    }

    public void setPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        this.prefix = normalizePath(prefix);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getName() {
        return name;
    }
}