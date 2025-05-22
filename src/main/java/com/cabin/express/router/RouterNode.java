package com.cabin.express.router;

import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RouterNode {
    // Static children (exact path segment matches)
    private final Map<String, RouterNode> staticChildren = new ConcurrentHashMap<>();

    // Dynamic child (path parameter, like :userId)
    private RouterNode dynamicChild;

    // Wildcard child (catch-all)
    private RouterNode wildcardChild;

    // Handlers for each HTTP method at this node
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    // Middleware specific to this node/path
    private final List<Middleware> middlewares = new ArrayList<>();

    // Is this node a mount point for a sub-router?
    private boolean isMountPoint;
    private Router mountedRouter;
    private String mountPrefix;

    // Parameter name for dynamic routes
    private String paramName;

    // Add a route with handler
    public void addHandler(String method, Handler handler) {
        handlers.put(method.toUpperCase(), handler);
    }

    // Add middleware to this node
    public void addMiddleware(Middleware middleware) {
        middlewares.add(middleware);
    }

    // Methods to build the trie
    public RouterNode getOrCreateStaticChild(String segment) {
        return staticChildren.computeIfAbsent(segment, k -> new RouterNode());
    }

    public RouterNode getOrCreateDynamicChild(String paramName) {
        if (dynamicChild == null) {
            dynamicChild = new RouterNode();
            this.paramName = paramName; // Store the parameter name
        }
        return dynamicChild;
    }

    public RouterNode getOrCreateWildcardChild() {
        if (wildcardChild == null) {
            wildcardChild = new RouterNode();
        }
        return wildcardChild;
    }

    // Getters
    public RouterNode getStaticChild(String segment) {
        return staticChildren.get(segment);
    }

    public RouterNode getDynamicChild() {
        return dynamicChild;
    }

    public String getParamName() {
        return paramName;
    }

    public RouterNode getWildcardChild() {
        return wildcardChild;
    }

    public Handler getHandler(String method) {
        return handlers.get(method.toUpperCase());
    }

    public List<Middleware> getMiddlewares() {
        return middlewares;
    }

    public boolean isMountPoint() {
        return isMountPoint;
    }

    public Router getMountedRouter() {
        return mountedRouter;
    }

    public String getMountPrefix() {
        return mountPrefix;
    }

    // Mount a sub-router at this node
    public void mountRouter(String prefix, Router router) {
        this.isMountPoint = true;
        this.mountedRouter = router;
        this.mountPrefix = prefix;
    }
}