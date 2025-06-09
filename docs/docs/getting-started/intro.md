---
id: intro
title: Introduction
---

# Welcome to CabinJ

[CabinJV]: src/main/java/com/cabin/express


## Introduction

CabinJ is a high-performance, lightweight HTTP server framework built with Java NIO for efficient non-blocking I/O
operations. It provides a simple yet powerful API for creating web applications with features like routing, middleware
support, and efficient request/response handling.

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