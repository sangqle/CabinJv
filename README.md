# CabinJ Framework

CabinJ is a simple and lightweight HTTP server framework using Java NIO. It allows you to create and manage routes,
apply middleware, and handle concurrent requests efficiently.

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
            res.send("Hello World");
        });
        server.use(router);
        server.start();
        System.err.println("Server started at http://localhost:8080");
    }
}
```

## Table of Contents

### Installation

```bash
git clone https://github.com/sangqle/CabinJv.git
cd CabinJv
```

```bash
 ./gradlew clean build
```

The build will generate a jar file in the `build/libs` directory. You can include this jar file in your project.

```bash
cp build/libs/cabin-1.0.1.jar /path/to/your/project/libs
```

### Usage
Add the jar file to your project's `build.gradle` file.

```groovy
dependencies {
    implementation files('libs/cabin-1.0.1.jar')
}
```

You can refer to the sample repository for more examples.

```bash
git clone https://github.com/sangqle/cabin-server-sample.git
```


## Docs & Community

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## License

This project is licensed under the MIT License.