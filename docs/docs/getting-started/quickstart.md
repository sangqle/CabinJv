---
id: quickstart
title: Quickstart
---

# Quickstart Guide

This guide will help you set up a simple CabinJ application in minutes. CabinJ is a lightweight, high-performance Java web framework built on Java NIO.

## Prerequisites

* JDK 17 or higher
* Maven or Gradle (recommended)
* Your favorite IDE (IntelliJ IDEA, Eclipse, VS Code, etc.)

## Creating a New Project

### Step 1: Set Up Your Project

Create a new Java project in your preferred IDE or using your build tool of choice.

### Step 2: Add CabinJ Dependency

Add CabinJ to your project dependencies as described in the [Installation Guide](installation.md).

#### Maven

```xml
<dependency>
    <groupId>com.cabin</groupId>
    <artifactId>cabinj</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Gradle

```groovy
implementation 'com.cabin:cabinj:1.0.0'
```

## Creating Your First Server

### Step 3: Create a Basic Server

Create a new Java class with a `main` method:

```java
package com.example;

import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.router.Router;

public class QuickStartApp {
    public static void main(String[] args) {
        // Create a new server listening on port 8080
        CabinServer server = new ServerBuilder()
            .setPort(8080)
            .build();
            
        // Create a router for handling HTTP requests
        Router router = new Router();
        
        // Define a simple route that responds with "Hello, CabinJ!"
        router.get("/", (req, res) -> {
            res.send("Hello, CabinJ!");
        });
        
        // Mount the router on the server
        server.use(router);
        
        // Start the server
        server.start();
        
        System.out.println("Server running at http://localhost:8080");
    }
}
```

### Step 4: Run Your Application

Run the `QuickStartApp` class in your IDE or build and run your project using Maven or Gradle.

Open your browser and navigate to `http://localhost:8080`. You should see "Hello, CabinJ!" displayed.

## Adding Routes and Handlers

### Step 5: Define Multiple Routes

Expand your application by adding more routes to handle different endpoints:

```java
// Simple GET route
router.get("/hello", (req, res) -> {
    res.send("Hello, World!");
});

// Route with path parameter
router.get("/users/:userId", (req, res) -> {
    String userId = req.getPathParam("userId");
    res.send("User ID: " + userId);
});

// POST route with JSON response
router.post("/api/items", (req, res) -> {
    // Get the request body as a Map
    Map<String, Object> requestBody = req.getBody();
    
    // Create a response object
    Map<String, Object> responseData = new HashMap<>();
    responseData.put("status", "success");
    responseData.put("message", "Item created");
    responseData.put("receivedData", requestBody);
    
    // Send JSON response
    res.send(responseData);
});
```

## Adding Middleware

### Step 6: Create and Use Middleware

Middleware functions allow you to process requests before they reach the route handlers:

```java
// Create a simple logging middleware
server.use((req, res, next) -> {
    System.out.println(req.getMethod() + " " + req.getPath());
    long startTime = System.currentTimeMillis();
    
    // Call the next middleware or route handler
    next.next(req, res);
    
    // Code after next() is executed after the response is processed
    long endTime = System.currentTimeMillis();
    System.out.println("Request processed in " + (endTime - startTime) + "ms");
});

// Mount the router after the middleware
server.use(router);
```

For more complex middleware, you can create a dedicated class:

```java
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.MiddlewareChain;
import java.io.IOException;

public class AuthMiddleware implements Middleware {
    @Override
    public void apply(Request req, Response res, MiddlewareChain next) throws IOException {
        // Check for authorization header
        String authHeader = req.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Process the token
            String token = authHeader.substring(7);
            
            // Validate token (simplified example)
            if (isValidToken(token)) {
                // Add user info to request attributes for use in handlers
                req.putAttribute("user", getUserFromToken(token));
                next.next(req, res);
                return;
            }
        }
        
        // Unauthorized
        res.setStatusCode(401);
        res.send("Unauthorized");
    }
    
    private boolean isValidToken(String token) {
        // Token validation logic would go here
        return true; // Simplified example
    }
    
    private Object getUserFromToken(String token) {
        // Extract user information from token
        return Map.of("id", "123", "username", "user");
    }
}
```

Then use it in your server setup:

```java
server.use(new AuthMiddleware());
```

## Organizing Routes with Multiple Routers

### Step 7: Create Modular Routers

For larger applications, organize your routes into separate routers:

```java
// Main router
Router mainRouter = new Router();

// API router for user-related endpoints
Router userRouter = new Router();
userRouter.get("/", (req, res) -> {
    res.send("User list");
});
userRouter.get("/:id", (req, res) -> {
    res.send("User details for: " + req.getPathParam("id"));
});

// API router for product-related endpoints
Router productRouter = new Router();
productRouter.get("/", (req, res) -> {
    res.send("Product list");
});
productRouter.get("/:id", (req, res) -> {
    res.send("Product details for: " + req.getPathParam("id"));
});

// Mount the specialized routers onto the main router at specific path prefixes
mainRouter.use("/users", userRouter);
mainRouter.use("/products", productRouter);

// Mount the main router on the server
server.use(mainRouter);
```

## Complete Example

Here's a complete example putting together everything we've covered:

```java
package com.example;

import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.router.Router;
import com.cabin.express.middleware.StaticMiddleware;

import java.util.HashMap;
import java.util.Map;

public class CompleteApp {
    public static void main(String[] args) {
        // Create server
        CabinServer server = new ServerBuilder()
            .setPort(8080)
            .build();
        
        // Create routers
        Router mainRouter = new Router();
        Router apiRouter = new Router();
        
        // Logging middleware
        server.use((req, res, next) -> {
            System.out.println("[" + req.getMethod() + "] " + req.getPath());
            next.next(req, res);
        });
        
        // Static file middleware for serving frontend assets
        server.use(new StaticMiddleware("public", "/"));
        
        // API routes
        apiRouter.get("/users", (req, res) -> {
            res.send(Map.of("users", new String[]{"John", "Jane", "Bob"}));
        });
        
        apiRouter.post("/login", (req, res) -> {
            Map<String, Object> body = req.getBody();
            String username = (String) body.get("username");
            String password = (String) body.get("password");
            
            // Simple authentication (in a real app, this would be more secure)
            if ("admin".equals(username) && "password".equals(password)) {
                res.send(Map.of("status", "success", "token", "sample-token-123"));
            } else {
                res.setStatusCode(401);
                res.send(Map.of("status", "error", "message", "Invalid credentials"));
            }
        });
        
        // Main routes
        mainRouter.get("/", (req, res) -> {
            res.setContentType("text/html");
            res.send("<h1>Welcome to CabinJ</h1><p>A lightweight Java web framework</p>");
        });
        
        mainRouter.get("/about", (req, res) -> {
            res.send("About CabinJ");
        });
        
        // Mount routers
        mainRouter.use("/api", apiRouter);
        server.use(mainRouter);
        
        // Start the server
        server.start();
        System.out.println("Server running at http://localhost:8080");
    }
}
```

## Next Steps

Now that you have a basic CabinJ application up and running, you can explore more advanced features:

- Learn about [Request](../core/request.md) and [Response](../core/response.md) objects
- Explore more advanced [Routing](../core/routing.md) techniques
- Understand how to use [Middleware](../core/middleware.md) effectively
- Add [Static File Serving](../guides/static-files.md) for your frontend assets
- Implement [Error Handling](../guides/error-handling.md) for robust applications

Check out the rest of our documentation for detailed information on all CabinJ features.