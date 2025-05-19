package com.cabin.express.router;

import com.cabin.express.interfaces.Middleware;
import com.cabin.express.server.CabinServer;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Tag("router-test")
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
            if (!stopped) {
                throw new IllegalStateException("Server failed to stop cleanly");
            }
        }
    }


    /**
     * Tests that the router correctly matches handlers to specific HTTP methods (GET and POST)
     * for the same path and ensures that the responses are handled appropriately.
     *
     * The test performs the following steps:
     * 1. Configures a router by registering separate `GET` and `POST` handlers for the same URI path.
     * 2. Sends an HTTP GET request to the configured path and verifies:
     *    - The response status code is 200.
     *    - The response body matches the expected output from the GET handler.
     * 3. Sends an HTTP POST request to the same URI path and verifies:
     *    - The response status code is 200.
     *    - The response body matches the expected output from the POST handler.
     *
     * This test ensures the router processes requests based on HTTP method distinction, even when the paths are identical.
     *
     * @throws IOException if an I/O error occurs during request handling.
     * @throws InterruptedException if the operation is interrupted during request processing.
     */
    @Test
    void shouldMatchSpecificHttpMethod() throws IOException, InterruptedException {
        // Setup router with GET and POST to same path
        Router router = new Router();
        router.get("/method-test", (req, res) -> res.send("GET response"));
        router.post("/method-test", (req, res) -> res.send("POST response"));
        server.use(router);

        // Test GET request matches only GET handler
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> getResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/method-test")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isEqualTo("GET response");

        // Test POST request matches only POST handler
        HttpResponse<String> postResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/method-test"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(postResponse.statusCode()).isEqualTo(200);
        assertThat(postResponse.body()).isEqualTo("POST response");
    }

    /**
     * Tests the router's ability to handle multiple path parameters in a route.
     *
     * This test performs the following steps:
     * 1. Configures a router with a route that includes two path parameters (`userId` and `postId`).
     * 2. Sends an HTTP GET request to the configured route with specific values for the path parameters.
     * 3. Verifies that:
     *    - The response status code is 200.
     *    - The response body correctly reflects the values of the path parameters.
     *
     * This ensures that the router can correctly extract and use multiple path parameters in a request.
     *
     * @throws IOException if an I/O error occurs during request handling.
     * @throws InterruptedException if the operation is interrupted during request processing.
     */
    @Test
    void shouldHandleMultiplePathParameters() throws IOException, InterruptedException {
        // Setup router with multiple path parameters
        Router router = new Router();
        router.get("/users/:userId/posts/:postId", (req, res) -> {
            String userId = req.getPathParam("userId");
            String postId = req.getPathParam("postId");
            res.send("User " + userId + ", Post " + postId);
        });
        server.use(router);

        // Test request with multiple parameters
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/users/123/posts/456")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("User 123, Post 456");
    }

    @Test
    void shouldHandleNestedRoutersWithPrefixes() throws IOException, InterruptedException {
        // Setup parent router with prefix
        Router apiRouter = new Router();
        apiRouter.setPrefix("/api");

        // Setup child router with additional prefix
        Router usersRouter = new Router();
        usersRouter.setPrefix("/users");
        usersRouter.get("/:id", (req, res) -> {
            res.send("User " + req.getPathParam("id"));
        });

        // Add child router to parent
        apiRouter.use(usersRouter);
        server.use(apiRouter);

        // Test nested route
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/123")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("User 123");
    }

    @Test
    void shouldExecuteRouteSpecificMiddleware() throws IOException, InterruptedException {
        // Setup router with route-specific middleware
        Router router = new Router();

        // Middleware that adds response header
        Middleware authMiddleware = (req, res, next) -> {
            res.setHeader("X-Auth-Status", "authenticated");
            next.next(req, res);
        };

        // Apply middleware only to protected route
        router.get("/public", (req, res) -> res.send("Public resource"));
        router.get("/protected", authMiddleware, (req, res) -> res.send("Protected resource"));

        server.use(router);

        // Test public route doesn't have auth header
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> publicResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/public")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(publicResponse.statusCode()).isEqualTo(200);
        assertThat(publicResponse.headers().firstValue("X-Auth-Status")).isEmpty();

        // Test protected route has auth header
        HttpResponse<String> protectedResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/protected")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(protectedResponse.statusCode()).isEqualTo(200);
        assertThat(protectedResponse.headers().firstValue("X-Auth-Status")).isPresent()
                .hasValue("authenticated");
    }

    @Test
    void shouldHandleExceptionsInRouteHandler() throws IOException, InterruptedException {
        // Setup router with handler that throws exception
        Router router = new Router();
        router.get("/error-route", (req, res) -> {
            throw new RuntimeException("Test exception");
        });
        server.use(router);

        // Test error handling
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/error-route")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("Error");
    }

    @Test
    void shouldParseJsonRequestBody() throws IOException, InterruptedException {
        // Setup router with JSON handling
        Router router = new Router();
        router.post("/json-endpoint", (req, res) -> {
            Map<String, Object> body = req.getBody();
            String name = (String) body.get("name");
            res.send("Hello, " + name);
        });
        server.use(router);

        // Send JSON request
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/json-endpoint"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"John\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Hello, John");
    }

    @Test
    void shouldSetAppropriateContentTypeForResponses() throws IOException, InterruptedException {
        // Setup router with different response types
        Router router = new Router();
        router.get("/text", (req, res) -> {
            res.setContentType("text/plain");
            res.send("Plain text");
        });
        router.get("/json", (req, res) -> {
            res.setContentType("application/json");
            res.send("{\"message\":\"JSON response\"}");
        });
        server.use(router);

        // Test text response
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> textResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/text")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(textResponse.statusCode()).isEqualTo(200);
        assertThat(textResponse.headers().firstValue("Content-Type")).isPresent()
                .hasValue("text/plain");

        // Test JSON response
        HttpResponse<String> jsonResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/json")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(jsonResponse.statusCode()).isEqualTo(200);
        assertThat(jsonResponse.headers().firstValue("Content-Type")).isPresent()
                .hasValue("application/json");
    }

    @Test
    void shouldHandlePathNormalization() throws IOException, InterruptedException {
        // Setup router
        Router router = new Router();
        router.get("/normalize-test", (req, res) -> res.send("Normalized path"));
        server.use(router);

        // Test with various path forms
        HttpClient client = HttpClient.newHttpClient();

        // Test double slashes
        HttpResponse<String> doubleSlashResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "//normalize-test")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Test trailing slash
        HttpResponse<String> trailingSlashResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/normalize-test/")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(doubleSlashResponse.statusCode()).isEqualTo(200);
        assertThat(trailingSlashResponse.statusCode()).isEqualTo(200);
    }

}