---
id: quickstart
title: Quickstart
---

# Quickstart Guide for CabinJ

CabinJ is a lightweight HTTP server framework built with Java NIO. This quickstart guide will help you set up a simple CabinJ server and get started with building web applications.
## Step 1: Create a New Java Project
Create a new Java project in your preferred IDE (like IntelliJ IDEA or Eclipse). Ensure you have JDK 17 or higher installed.

## Step 2: Add CabinJ Dependency
You can include CabinJ in your project using jar file or build tools like Maven or Gradle. As mentioned in the [Installation Guide](installation.md).

## Step 3: Create a Simple HTTP Server
Create a new Java class, for example, `CabinServerSimple.java`, and add the following code:
```java
import com.cabin.express.CabinServer;
import com.cabin.express.Router;
import java.io.IOException;

public class CabinServerSimple {
    public static void main(String[] args) throws IOException {
        CabinServer server = new CabinServer.Builder().setPort(8080).build();
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

## Step 4: Run the Server
Run the `CabinServerSimple` class. You should see the message "Server started at http://localhost:8080" in the console.
You can now open your web browser and navigate to `http://localhost:8080` to see the "Hello World" message.

## Step 5: Add Middleware (Optional)
You can enhance your server with middleware for tasks like authentication or logging. Create a new class, for example, `JwtMiddleware.java`, and add the following code:
```java
import com.cabin.express.Middleware;
import com.cabin.express.Request;
import com.cabin.express.Response;
import com.cabin.express.MiddlewareChain;
import java.io.IOException;

public class JwtMiddleware implements Middleware {
    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        System.err.println("Authentication here and call next()");
        next.next(request, response);
    }
}
```

Then, you can use this middleware in your server setup:
```java
server.use(new JwtMiddleware());
```

## Step 6: Create a Router
You can create a router to handle different routes in your application. Create a new class, for example, `AppRouter.java`, and add the following code:
```java
import com.cabin.express.Router;
import com.cabin.express.Request;
import com.cabin.express.Response;
public class AppRouter {
    public Router create() {
        Router router = new Router();
        router.get("/hello", this::hello);
        return router;
    }

    private void hello(Request req, Response resp) {
        resp.send("Hello from Cabin");
    }
}
```

Then, use this router in your server setup:
```java
server.use("/api/v1/", new AppRouter().create());
```

The final server setup in `CabinServerSimple.java` would look like this:
```java
import com.cabin.express.CabinServer;
import com.cabin.express.Router;
import java.io.IOException;
public class CabinServerSimple {
    public static void main(String[] args) throws IOException {
        CabinServer server = new CabinServer.Builder().setPort(8080).build();
        Router router = new AppRouter().create();

        server.use(new JwtMiddleware()); // Optional middleware
        server.use("/api/v1/", router);
        server.start();
        System.err.println("Server started at http://localhost:8080/");
    }
}
```

## Step 7: Start the Server
Finally, start your server by running the `CabinServerSimple` class again. You can now access the `/api/v1/hello` route at `http://localhost:8080/api/v1/hello`.


----

## Conclusion
You have successfully set up a simple CabinJ server and created routes and middleware. You can now expand your application by adding more routes, middleware, and features as needed. For more advanced usage.