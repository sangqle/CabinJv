[CabinJV]: src/main/java/com/cabin/express

# CabinJ Framework

## Introduction

CabinJ is a high-performance, lightweight HTTP server framework built with Java NIO for efficient non-blocking I/O
operations. It provides a simple yet powerful API for creating web applications with features like routing, middleware
support, and efficient request/response handling.

## 📦 Releases

See [RELEASE.md](RELEASE.md) for full release history and upgrade notes.

## Framework Architecture

### Core Components

#### Server Layer

- [**CabinServer**](src/main/java/com/cabin/express/server/CabinServer.java): The main entry point that manages server lifecycle, socket connections, and request delegation
- [**ServerBuilder**](src/main/java/com/cabin/express/server/ServerBuilder.java): Builder pattern implementation for configuring and creating CabinServer instances
- [**BufferPool**](src/main/java/com/cabin/express/server/BufferPool.java): Manages reusable ByteBuffer instances for memory efficiency

#### Request/Response Handling

- [**Request**](src/main/java/com/cabin/express/http/Request.java): Represents an HTTP request with methods to access headers, parameters, body, etc.
- [**Response**](src/main/java/com/cabin/express/http/Response.java): Represents an HTTP response with methods for headers, status codes, and writing the response body
- [**Router**](src/main/java/com/cabin/express/router/Router.java): Defines and handles HTTP routes and maps them to appropriate handlers

#### Middleware System

- [**Middleware**](src/main/java/com/cabin/express/interfaces/Middleware.java): Interface for creating middleware components
- [**MiddlewareChain**](src/main/java/com/cabin/express/middleware/MiddlewareChain.java): Manages the execution flow of middleware and route handlers
- **Built-in Middleware**:
   - [**CORS**](src/main/java/com/cabin/express/middleware/Cors.java): Cross-Origin Resource Sharing support
   - [**GzipMiddleware**](src/main/java/com/cabin/express/middleware/GzipMiddleware.java): Compresses responses with gzip encoding

#### Concurrency Management

- [**CabinWorkerPool**](src/main/java/com/cabin/express/worker/CabinWorkerPool.java): Thread pool implementation for handling concurrent requests

#### Configuration & Utilities

- [**ConfigLoader**](src/main/java/com/cabin/express/config/ConfigLoader.java): Loads configuration properties from various sources
- [**Environment**](src/main/java/com/cabin/express/config/Environment.java): Provides access to configuration values throughout the application
- [**CabinLogger**](src/main/java/com/cabin/express/loggger/CabinLogger.java): Logging utilities for the framework

## Framework Flow

1. **Initialization**:
    - Create a [CabinServer](src/main/java/com/cabin/express/server/CabinServer.java) instance
      using [ServerBuilder](src/main/java/com/cabin/express/server/ServerBuilder.java)
    - Configure routes with [Router](src/main/java/com/cabin/express/router/Router.java) instances
    - Apply global middleware
    - Start the server

2. **Request Processing**:
    - Server accepts incoming connections via NIO Selector
    - Requests are read asynchronously into ByteBuffer pools
    - Complete requests are parsed into [Request](src/main/java/com/cabin/express/http/Request.java) objects
    - Requests are dispatched to worker threads for processing

3. **Route Handling**:
    - Router matches request path and method to registered routes
    - [Middleware](src/main/java/com/cabin/express/interfaces/Middleware.java) is applied in sequence
      via [MiddlewareChain](src/main/java/com/cabin/express/middleware/MiddlewareChain.java)
    - Route handler processes the request and populates the response
    - [Response](src/main/java/com/cabin/express/http/Response.java) is sent back to the client

4. **Resource Management**:
    - Idle connections are monitored and cleaned up
    - [ByteBuffer](src/main/java/com/cabin/express/server/BufferPool.java) pools are reused for memory efficiency
    - Worker thread pools scale based on load
      via [CabinWorkerPool](src/main/java/com/cabin/express/worker/CabinWorkerPool.java)


## Example Usage

### Simple HTTP Server

```java
public class CabinServerSimple {
   public static void main(String[] args) throws IOException {
      CabinServer server = new ServerBuilder().setPort(8080).build();
      Router router = new Router();
      router.get("/", (req, res) -> {
         res.writeBody("Hello World");
         res.send();
      });
      server.use(router);
      server.start();
      System.err.println("Server started at http://localhost:8080");
   }
}
```

### CabinServer with Middleware

```java
class JwtMiddleware implements Middleware {

    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        System.err.println("Authentication here and call next()");
        next.next(request, response);
    }
}

class CustomMiddleware implements Middleware {
    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        System.err.println("Custom middleware logic here");
        // You can modify the request or response if needed
        next.next(request, response);
    }
}

class AppRouter {
    public Router create() {
        Router router = new Router();
        router.get("/hello", this::hello);
        return router;
    }

    private void hello(Request req, Response resp) {
        resp.send("Hello from Cabin");
    }
}

public class CabinServerWithMiddleware {
    private static final Logger logger = LoggerFactory.getLogger(CabinServerWithMiddleware.class);

    public static void main(String[] args) throws IOException {
        CabinServer server;
        server = new ServerBuilder()
                .setPort(8888)
                .enableProfiler(true)
                .enableProfilerDashboard(true)
                .build();


        Router appRouter = new AppRouter().create();
        appRouter.use(new JwtMiddleware());
        appRouter.use(new CustomMiddleware());

        server.use("/api", appRouter);

        server.start();
    }
}
```

## Getting Started for Contributors

### Setting Up the Development Environment

1. **Clone the repository**:

```shell script
git clone https://github.com/sangqle/CabinJv.git
   cd CabinJv
```

2. **Build the project**:

```shell script
./gradlew clean build
```

3. **Run the tests**:

```shell script
./gradlew test
```

### Project Structure

```
com.cabin.express/
├── config/             # Configuration classes
│   ├── ConfigLoader.java
│   └── Environment.java
├── http/               # HTTP request/response handling
│   ├── Request.java
│   ├── Response.java
│   ├── MultipartParser.java
│   └── UploadedFile.java
├── interfaces/         # Core interfaces
│   ├── Handler.java
│   └── Middleware.java
├── loggger/            # Logging utilities
│   └── CabinLogger.java
├── middleware/         # Middleware implementations
│   ├── Cors.java
│   ├── GzipMiddleware.java
│   └── MiddlewareChain.java
├── router/             # Routing system
│   └── Router.java
├── server/             # Server implementation
│   ├── BufferPool.java
│   ├── CabinServer.java
│   ├── Monitor.java
│   └── ServerBuilder.java
├── stream/             # Stream implementations
│   ├── NonBlockingOutputStream.java
│   └── SimpleChunkedOutputStream.java
└── worker/             # Concurrency utilities
    └── CabinWorkerPool.java
```

### How to Contribute

1. **Understanding the codebase**:
    - Start with the simple examples (HServerSample.java)
    - Explore the core classes (CabinServer, Router, Request, Response)
    - Review the middleware system for extension points

2. **Adding a new feature**:
    - For new middleware: Implement the Middleware interface
    - For utility functions: Add them to appropriate classes
    - For new core components: Discuss with maintainers first

3. **Testing**:
    - Write unit tests for your code
    - Use SimpleMainTests to create integration tests

4. **Submitting changes**:
    - Create a branch for your feature
    - Submit a pull request with a clear description
    - Ensure all tests pass

## Common Development Tasks

### Creating a Custom Middleware

```java
public class LoggingMiddleware implements Middleware {
    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        long startTime = System.currentTimeMillis();

        // Continue to the next middleware or route handler
        next.next(request, response);

        // Log after processing
        long duration = System.currentTimeMillis() - startTime;
        System.out.println(request.getMethod() + " " + request.getPath() + " - " + duration + "ms");
    }
}
```

### Registering Routes

```java
Router router = new Router();

// Basic route
router.

get("/hello",(req, res) ->{
        res.

writeBody("Hello World");
    res.

send();
});

// Path parameters
        router.

get("/users/:userId",(req, res) ->{
String userId = req.getPathParam("userId");
    res.

writeBody("User ID: "+userId);
    res.

send();
});

// JSON response

router.post("/api/data",(req, res) ->{
Map<String, Object> data = req.getBody();

// Process data
res.send(responseObject); // Automatically serialized to JSON
});
```

### Server Configuration

```java
CabinServer server = new ServerBuilder()
        .setPort(8080)               // HTTP port
        .setDefaultPoolSize(20)      // Default thread pool size
        .setMaxPoolSize(100)         // Maximum threads
        .setMaxQueueCapacity(1000)   // Request queue capacity
        .enableLogMetrics(true)      // Enable performance logging
        .build();

// Add global middleware
server.

use(new LoggingMiddleware());
        server.

use(new GzipMiddleware());

// Add routers
server.use(apiRouter);

// Start the server
server.start();
```

## Performance Considerations

- The framework uses NIO for non-blocking I/O, making it efficient for handling many concurrent connections
- Worker pools manage thread usage to prevent resource exhaustion
- Buffer pooling reduces memory allocation/garbage collection overhead
- Connection timeouts and idle connection cleanup prevent resource leaks
- Monitoring tools are available to track resource usage

## Next Steps for New Contributors

1. Review the existing code to understand the framework design
2. Run the example applications to see the framework in action
3. Check the issue tracker for beginner-friendly tasks
4. Consider implementing a small feature or enhancement
5. Contribute documentation improvements

By following this guide, you'll be able to understand, use, and contribute to the CabinJ framework efficiently.


