package com.cabin.express.router;

// In your test class (e.g., RouterTest.java)
import com.cabin.express.server.CabinServer;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
// ... other imports

public class RouterTest {
    private CabinServer server;
    private int serverPort;
    private String baseUrl;
    // private Thread serverThread; // If ServerTestUtil returns it

    @BeforeEach
    void setUp() throws Exception { // Allow exceptions
        // Assuming ServerTestUtil.startServerWithDynamicPort() is updated
        // to return an object or array with server, port, and thread
        Object[] serverSetup = ServerTestUtil.startServerWithDynamicPort();
        server = (CabinServer) serverSetup[0];
        serverPort = (int) serverSetup[1]; // Get the actual port
        // serverThread = (Thread) serverSetup[2]; // If needed for explicit join
        baseUrl = "http://localhost:" + serverPort;

        // Optional: Wait for server to be ready if startServerWithDynamicPort doesn't guarantee it
        boolean isReady = ServerTestUtil.waitForServerReady(baseUrl, "/some-health-check-path", 5000);
        if (!isReady) {
            // Clean up immediately if server didn't start
            if (server != null) ServerTestUtil.stopServer(server, 1000);
            throw new IllegalStateException("Server failed to start or become ready in time for test.");
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) {
            boolean stopped = ServerTestUtil.stopServer(server, 5000);
            // Add assertion if desired, e.g., assertThat(stopped).isTrue();
            // if (serverThread != null) {
            //     serverThread.join(5000); // Ensure thread finishes
            // }
        }
    }

    // ... Your @Test methods ...
}