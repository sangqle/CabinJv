package com.cabin.express.server;

import com.cabin.express.router.Router;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SimpleMainTests {
    @Test
    void testServer() throws IOException {
        // Use dynamic port allocation
        int port = ServerTestUtil.findAvailablePort();
        
        CabinServer server = new ServerBuilder()
                .setPort(port)
                .build();

        Router router = new Router();
        router.get("/", (req, res) -> {
            res.writeBody("Hello World");
            res.send();
        });
        server.use(router);
        
        // Start server in background
        Thread serverThread = ServerTestUtil.startServerInBackground(server);
        String baseUrl = "http://localhost:" + port;
        System.err.println("Server started at " + baseUrl);
        
        // Wait for server to be ready
        boolean isReady = ServerTestUtil.waitForServerReady(baseUrl, "/", 5000);
        assertThat(isReady).isTrue();
        
        // Do some operations with the server
        
        // Stop the server
        boolean stopped = ServerTestUtil.stopServer(server, 5000);
        assertThat(stopped).isTrue();
    }
}