# CabinJ Framework

CabinJ is a simple and lightweight HTTP server framework using Java NIO. It allows you to create and manage routes, apply middleware, and handle concurrent requests efficiently.

## Features

- Lightweight and fast
- Middleware support
- Concurrent request handling
- Easy to use

## Getting Started

### Prerequisites

- Java 11 or higher
- Gradle

### Installation

Clone the repository:

```sh
git clone https://github.com/yourusername/cabinj.git
cd cabinj
```

Build the project:

```sh
./gradlew build
```

### Usage

Create a new server using `ServerBuilder`:

```java
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.zdemo.AuthMiddleware;
import com.cabin.express.zdemo.AppRouter;
import com.cabin.express.zdemo.ApiRouter;

public class Main {
    public static void main(String[] args) {
        CabinServer server = new ServerBuilder().build();

        // Add global middleware
        server.use(AuthMiddleware.Instance::checkAuth);

        // Register routes
        server.use(AppRouter.Instance.registerRoutes());
        server.use(ApiRouter.Instance.registerRoutes());

        // Start the server
        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Creating Routes

Define routes in a router:

```java
import com.cabin.express.router.Router;

public class AppRouter {
    public static final AppRouter Instance = new AppRouter();

    public Router registerRoutes() {
        Router router = new Router();

        router.get("/", (req, res) -> {
            res.writeBody("Welcome to CabinJ!");
            res.send();
        });

        router.post("/data", (req, res) -> {
            res.writeBody("Data received");
            res.send();
        });

        return router;
    }
}
```

### Adding Middleware

Create middleware to handle requests:

```java
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.middleware.MiddlewareChain;

import java.io.IOException;

public class AuthMiddleware {
    public static final AuthMiddleware Instance = new AuthMiddleware();

    private AuthMiddleware() {
    }

    public void checkAuth(Request req, Response res, MiddlewareChain next) throws IOException {
        String token = req.getHeader("Authorization");
        if (token == null || !token.equals("Bearer token")) {
            res.setStatusCode(401);
            res.writeBody("Unauthorized");
            res.send();
            return;
        }
        next.next(req, res);
    }
}
```

### Logging

Use `CabinLogger` for logging:

```java
import com.cabin.express.loggger.CabinLogger;

public class Main {
    public static void main(String[] args) {
        CabinLogger.setDebug(true);
        CabinLogger.info("Starting CabinJ Framework...");
    }
}
```

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## License

This project is licensed under the MIT License.