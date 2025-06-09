---
id: middleware
title: Middleware
---

# Middleware in CabinJ

Middleware functions are a powerful feature in CabinJ that allow you to execute code during the request-response cycle. They can perform a wide variety of tasks, such as logging, authentication, data validation, compression, serving static files, and error handling.

Middleware functions have access to the `Request` object, the `Response` object, and the `next` middleware function in the application’s request-response cycle. The `next` middleware function is a function in the `MiddlewareChain` which, when invoked, executes the middleware succeeding the current middleware.

## Core Concepts

*   **Interface**: Middleware in CabinJ is defined by implementing the `Middleware` interface.
*   **`apply` Method**: This interface has a single method: `void apply(Request req, Response res, MiddlewareChain next) throws IOException;`.
    *   `req`: The current HTTP request.
    *   `res`: The current HTTP response.
    *   `next`: A `MiddlewareChain` object that, when its `next(req, res)` method is called, passes control to the next middleware in the stack or to the final route handler.
*   **Order of Execution**: Middleware is executed in the order it is added.
*   **Control Flow**: A middleware can:
    *   Perform operations on the request or response.
    *   End the request-response cycle (e.g., by sending a response for authentication failure).
    *   Call the `next.next(req, res)` method to pass control to the next middleware.
    *   Modify the request or response objects before passing them to the next middleware.

## Creating Middleware

To create a middleware, implement the `Middleware` interface:

```java
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.MiddlewareChain;
import java.io.IOException;

public class LoggerMiddleware implements Middleware {
    @Override
    public void apply(Request req, Response res, MiddlewareChain next) throws IOException {
        System.out.println("Request received: " + req.getMethod() + " " + req.getPath());

        // Call the next middleware or handler in the chain
        next.next(req, res);

        System.out.println("Response sent with status: " + res.getStatusCode());
    }
}
```

## Using Middleware

Middleware can be applied at different levels: server-level (global for all requests), router-level (global for a specific router), or route-specific.

### 1. Server-Level Middleware

Applied to every request handled by the `CabinServer` instance.

```java
CabinServer server = new ServerBuilder().setPort(8080).build();

// Apply LoggerMiddleware to all incoming requests
server.use(new LoggerMiddleware());

Router mainRouter = new Router();
mainRouter.get("/", (req, res) -> res.send("Hello World!"));
server.use(mainRouter);

server.start();
```

### 2. Router-Level Middleware

Applied to every request handled by a specific `Router` instance and its sub-routers.

```java
Router apiRouter = new Router();

// This middleware will apply to all routes defined in apiRouter
apiRouter.use((req, res, next) -> {
    System.out.println("API Router Middleware: Path " + req.getPath());
    // Example: Add a custom header for all API responses
    res.setHeader("X-API-Version", "1.0");
    next.next(req, res);
});

apiRouter.get("/users", (req, res) -> res.json(Map.of("user", "John Doe")));
apiRouter.get("/products", (req, res) -> res.json(Map.of("product", "Laptop")));

CabinServer server = new ServerBuilder().build();
server.use("/api", apiRouter); // Mount the router with its middleware
server.start();
```

### 3. Route-Specific Middleware

Applied only to specific routes.

```java
Router router = new Router();

Middleware authMiddleware = (req, res, next) -> {
    String token = req.getHeader("Authorization");
    if (token != null && "Bearer valid-token".equals(token)) {
        System.out.println("Authentication successful for: " + req.getPath());
        next.next(req, res); // Proceed to the handler
    } else {
        System.out.println("Authentication failed for: " + req.getPath());
        res.setStatusCode(401).send("Unauthorized");
        // Do not call next.next() to stop processing
    }
};

// Apply authMiddleware only to the /secure-data route
router.get("/secure-data", authMiddleware, (req, res) -> {
    res.send("This is highly sensitive data.");
});

router.get("/public-data", (req, res) -> {
    res.send("This data is public.");
});

CabinServer server = new ServerBuilder().build();
server.use(router);
server.start();
```
You can also pass a list of middleware:
```java
List<Middleware> securityChecks = List.of(authMiddleware, anotherCheckMiddleware);
router.get("/super-secure", securityChecks, (req, res) -> {
    res.send("This is super secure data.");
});
```

## Middleware Chain (`MiddlewareChain`)

The `MiddlewareChain` class is responsible for managing the execution flow of middleware and the final route handler.
When `chain.next(req, res)` is called within a middleware:
1.  If there are more middleware functions in the chain, the `apply` method of the next middleware is invoked.
2.  If all middleware functions have been executed, the final `routeHandler.handle(req, res)` is invoked.

This mechanism allows for pre-processing of requests and post-processing of responses.

## Built-in Middleware: `StaticMiddleware`

CabinJ includes `StaticMiddleware` for serving static files (e.g., HTML, CSS, JavaScript, images).

```java
import com.cabin.express.middleware.StaticMiddleware;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.router.Router;

// ...

// Assuming you have a directory "public" with static files
// e.g., public/index.html, public/css/style.css

// Serve files from the "public" directory under the "/static" URL prefix
StaticMiddleware staticMiddleware = new StaticMiddleware("public", "/static");

// You can exclude certain paths or routers from being handled by StaticMiddleware
Router apiRouter = new Router();
apiRouter.get("/data", (req, res) -> res.json(Map.of("message", "API Data")));

// Exclude the /api prefix so StaticMiddleware doesn't try to serve files for API calls
staticMiddleware.excludePrefixes("/api");
// Alternatively, you could exclude the router instance if it's mounted at root
// staticMiddleware.excludeRouters(apiRouter);


CabinServer server = new ServerBuilder().setPort(8080).build();

// Add StaticMiddleware first, so it can serve files
server.use(staticMiddleware);

// Then add your API router
server.use("/api", apiRouter);

// Add a root handler as a fallback or for the main page if not served by static
server.get("/", (req, res) -> res.send("Welcome to CabinJ!"));

server.start();
```
In this example:
*   Requests to `/static/index.html` would serve `public/index.html`.
*   Requests to `/static/css/style.css` would serve `public/css/style.css`.
*   Requests to `/api/data` would be handled by `apiRouter` and not `StaticMiddleware`.
*   Requests to `/` would be handled by the server's root GET handler.

If `StaticMiddleware` is configured with a root URL prefix (e.g., `/`), it can serve `index.html` by default for requests to `/`.

```java
// Serve files from "public" directory at the root URL
StaticMiddleware rootStaticMiddleware = new StaticMiddleware("public", "/");
rootStaticMiddleware.excludePrefixes("/api"); // Still exclude API

CabinServer server = new ServerBuilder().setPort(8080).build();
server.use("/api", apiRouter); // Mount API router first if static is at root
server.use(rootStaticMiddleware); // Static middleware handles remaining requests

server.start();
// Now, a request to "/" might serve "public/index.html"
```

Middleware is a fundamental concept for building robust and modular web applications with CabinJ.
