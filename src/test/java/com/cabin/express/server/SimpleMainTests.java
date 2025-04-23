package com.cabin.express.server;

import com.cabin.express.router.Router;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class SimpleMainTests {
//    @Test
    void testServer() throws IOException {
        CabinServer server = new ServerBuilder()
                .setPort(8080)
                .enableLogMetrics(false)
                .build();

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
