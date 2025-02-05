# CabinJ Framework

CabinJ is a simple and lightweight HTTP server framework using Java NIO. It allows you to create and manage routes, apply middleware, and handle concurrent requests efficiently.

```java
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

import java.io.IOException;

public class HServerSample {
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

## Table of Contents

## Docs & Community

## Getting Started

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## License

This project is licensed under the MIT License.