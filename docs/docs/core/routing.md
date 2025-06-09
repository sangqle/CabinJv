---
id: routing
title: Routing
---

# Routing with CabinJ

CabinJ's `Router` provides a flexible and powerful way to handle HTTP requests by mapping URLs and HTTP methods to specific handlers. It uses a trie-based structure for efficient route matching, supporting static paths, path parameters, and wildcards.

## Core Concepts

*   **Trie-based Matching**: Routes are stored in a trie (prefix tree) for fast lookups.
*   **Path Segments**:
    *   **Static Segments**: Exact matches (e.g., `/users`, `/products`).
    *   **Dynamic Segments (Parameters)**: Capture parts of the URL path (e.g., `/users/:id`, where `:id` becomes a request parameter).
    *   **Wildcard Segments**: Match any sequence of characters at the end of a path (e.g., `/files/*`).
*   **HTTP Methods**: Routes can be defined for specific HTTP methods (GET, POST, PUT, DELETE, etc.) or for all methods.
*   **Middleware**: Middleware can be applied globally to all routes within a router or specifically to individual routes.
*   **Sub-Routers**: Routers can be nested (mounted) to organize routes into logical groups.

## Creating a Router

You can create a new router instance easily:

```java
import com.cabin.express.router.Router;

Router mainRouter = new Router();
```

## Defining Routes

The `Router` class provides convenient methods for each HTTP verb:

### Basic Routes

```java
Router router = new Router();

// GET request to /hello
router.get("/hello", (req, res) -> {
    res.send("Hello, World!");
});

// POST request to /submit-data
router.post("/submit-data", (req, res) -> {
    // Process request body
    String data = req.getBodyAsString();
    res.json(Map.of("status", "success", "received", data));
});

// Other methods: put(), delete(), patch(), options()
router.put("/items/:id", (req, res) -> { /* ... */ });
router.delete("/items/:id", (req, res) -> { /* ... */ });
```

### Routes with Path Parameters

Path parameters allow you to capture values from the URL. Parameters are prefixed with a colon (`:`).

```java
router.get("/users/:userId/orders/:orderId", (req, res) -> {
    String userId = req.getParam("userId");
    String orderId = req.getParam("orderId");
    res.send("User ID: " + userId + ", Order ID: " + orderId);
});
```
The captured values are available via `req.getParam("paramName")`.

### Wildcard Routes

A wildcard `*` can be used at the end of a path to match anything that follows.

```java
router.get("/files/*", (req, res) -> {
    // req.getPath() will give the full path matched by the wildcard
    String filePath = req.getPath().substring("/files/".length()); // Example to get the part after /files/
    res.send("Accessing file: " + filePath);
});
```

### `router.all()`

The `all()` method registers a handler for all HTTP methods for a given path.

```java
router.all("/admin/*", (req, res) -> {
    // This handler will be called for GET, POST, PUT, etc. to /admin/some/path
    System.out.println("Admin area accessed: " + req.getMethod() + " " + req.getPath());
    // Potentially call next.handle(req, res) if this is middleware-like
});
```

## Middleware

Middleware functions can process requests before they reach the main handler or modify the response.

### Global Middleware

Apply middleware to all routes defined within a router using `router.use(Middleware)`.

```java
// Example: A simple logging middleware
Middleware loggingMiddleware = (req, res, next) -> {
    System.out.println("Request: " + req.getMethod() + " " + req.getPath());
    next.handle(req, res); // Call the next middleware or handler
    System.out.println("Response status: " + res.getStatusCode());
};

router.use(loggingMiddleware);

router.get("/home", (req, res) -> res.send("Welcome Home!"));
```
All requests handled by this router will first pass through `loggingMiddleware`.

### Route-Specific Middleware

You can also apply middleware to specific routes.

```java
Middleware authMiddleware = (req, res, next) -> {
    String token = req.getHeader("Authorization");
    if (token != null && token.equals("secret-token")) {
        next.handle(req, res);
    } else {
        res.setStatusCode(401).send("Unauthorized");
    }
};

// Single middleware
router.get("/secure-data", authMiddleware, (req, res) -> {
    res.send("This is secure data.");
});

// Multiple middlewares
List<Middleware> securityChecks = List.of(authMiddleware, anotherCheckMiddleware);
router.get("/super-secure-data", securityChecks, (req, res) -> {
    res.send("This is super secure data.");
});
```

## Sub-Routers (Mounting Routers)

You can organize your application by breaking it into smaller, manageable routers and then mounting them onto a parent router.

### Mounting at a Path Prefix

Use `router.use(String pathPrefix, Router childRouter)` to mount a `childRouter` at a specific `pathPrefix`.

```java
Router mainRouter = new Router();
Router userRouter = new Router();

userRouter.get("/", (req, res) -> res.send("User index")); // Will match /users
userRouter.get("/:id", (req, res) -> { // Will match /users/:id
    String userId = req.getParam("id");
    res.send("Profile for user " + userId);
});

mainRouter.use("/users", userRouter); // Mount userRouter under /users

// Now, a GET request to /users will be handled by userRouter's "/" handler.
// A GET request to /users/123 will be handled by userRouter's "/:id" handler.
```

### Mounting at the Root

You can also mount a router at the root of another router using `router.use(Router childRouter)`. This is less common for top-level routers but can be useful for composing middleware or default handlers.

```java
Router baseRouter = new Router();
Router featureRouter = new Router();

featureRouter.get("/feature1", (req, res) -> res.send("Feature 1"));
baseRouter.use(featureRouter); // All routes from featureRouter are added to baseRouter

// A GET request to /feature1 on baseRouter will be handled.
```

## How it Works (Internals)

The `Router` uses a `RouterNode` class to build a trie structure.
*   Each `RouterNode` can have static children (for exact path segments), a dynamic child (for parameters like `:id`), and a wildcard child (`*`).
*   Handlers and route-specific middleware are stored at the `RouterNode` corresponding to the end of a route path.
*   When a request comes in, the router traverses this trie based on the request path segments to find the matching `RouterNode` and its associated handler and middleware.
*   Path normalization ensures that paths like `/path`, `/path/`, and `path` are treated consistently.

## Using Routers with `CabinServer`

Typically, you create a main router and then pass it to your `CabinServer` instance. The `Router` itself implements the `Middleware` interface, so it can be directly used by the server.

```java
// In your main application setup:
CabinServer server = new ServerBuilder()
    .setPort(8080)
    .build();

Router appRouter = new Router();

// Define global middleware for the appRouter
appRouter.use((req, res, next) -> {
    System.out.println("App-level middleware: " + req.getPath());
    next.handle(req, res);
});

// Define routes on appRouter
appRouter.get("/", (req, res) -> res.send("Welcome to the Cabin!"));

// Create and mount sub-routers
Router apiRouter = new Router();
apiRouter.get("/version", (req, res) -> res.json(Map.of("version", "1.0.0")));
appRouter.use("/api", apiRouter);

// Use the main router with the server
server.use(appRouter);

server.start();
```
In this setup, `CabinServer` will delegate incoming requests to `appRouter`, which will then use its routing logic (including any mounted sub-routers like `apiRouter`) to find and execute the appropriate handler and middleware chain.