package com.cabin.express.router;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class RouterTest {

    private CabinServer server;
    private int port;
    private String baseUrl;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws IOException {
        // Use dynamic port allocation
        port = ServerTestUtil.findAvailablePort();
        baseUrl = "http://localhost:" + port;

        // Setup test server
        server = new ServerBuilder()
                .setPort(port)
                .build();

        // Start server in background thread
        serverThread = ServerTestUtil.startServerInBackground(server);

        // Add a health check endpoint
        Router healthRouter = new Router();
        healthRouter.get("/health", (req, res) -> res.send("OK"));
        server.use(healthRouter);

        // Verify server is ready
        boolean isReady = ServerTestUtil.waitForServerReady(baseUrl, "/health", 5000);
        if (!isReady) {
            fail("Server failed to start in time");
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            boolean stopped = ServerTestUtil.stopServer(server, 0);
            assertThat(stopped).withFailMessage("Server failed to stop cleanly").isTrue();
        }
    }

    @Test
    void shouldHandleNestedRoutersWithPrefixes() throws IOException, InterruptedException {
        // Setup parent router
        Router apiRouter = new Router();

        // Setup child router
        Router usersRouter = new Router();
        usersRouter.get("/:id", (req, res) -> {
            res.send("User " + req.getPathParam("id"));
        });

        // Add child router with path prefix
        apiRouter.use("/users", usersRouter);

        // Mount the parent router to the server with a prefix
        server.use("/api", apiRouter);

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
    void shouldHandleMultipleLevelsOfNestedRouters() throws IOException, InterruptedException {
        // Setup top-level router
        Router apiRouter = new Router();

        // Setup mid-level router
        Router usersRouter = new Router();

        // Setup leaf-level router
        Router profileRouter = new Router();
        profileRouter.get("/details/:id", (req, res) -> {
            res.send("Profile details for user " + req.getPathParam("id"));
        });

        // Mount routers hierarchically
        usersRouter.use("/profile", profileRouter);
        apiRouter.use("/users", usersRouter);
        server.use("/api", apiRouter);

        // Test deeply nested route
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/profile/details/456")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Profile details for user 456");
    }

    @Test
    void shouldExtractParametersAcrossNestedRouters() throws IOException, InterruptedException {
        // Setup parent router with parameter
        Router apiRouter = new Router();

        // Setup child router that will use parent's parameter
        Router userRouter = new Router();
        userRouter.get("/posts/:postId", (req, res) -> {
            String userId = req.getPathParam("userId");
            String postId = req.getPathParam("postId");
            res.send("User " + userId + ", Post " + postId);
        });

        // Mount with parameterized path
        apiRouter.use("/users/:userId", userRouter);
        server.use("/api", apiRouter);

        // Test nested route with parameters at different levels
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/789/posts/42")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("User 789, Post 42");
    }

    @Test
    void shouldHandleNestedParameterizedRoutes() throws IOException, InterruptedException {
        // Setup parent router
        Router apiRouter = new Router();

        // Setup child router
        Router userRouter = new Router();

        // Add a simple route to the child router that uses parent's parameter
        userRouter.get("/info", (req, res) -> {
            String userId = req.getPathParam("userId");
            res.send("User info for: " + userId);
        });

        // Mount with parameterized path
        apiRouter.use("/users/:userId", userRouter);

        // Mount on the server
        server.use("/api", apiRouter);

        // Test the nested parameterized route
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/users/123/info"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("User info for: 123");
    }

    @Test
    void shouldHandleWildcardRoutesInNestedContext() throws IOException, InterruptedException {
        // Setup routers
        Router apiRouter = new Router();
        Router filesRouter = new Router();

        // Add wildcard route
        filesRouter.get("/*", (req, res) -> {
            String wildcard = req.getPathParam("wildcard");
            res.send("Serving file: " + wildcard);
        });

        // Mount routers
        apiRouter.use("/files", filesRouter);
        server.use("/api", apiRouter);

        // Test wildcard route
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/files/images/logo.png")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Serving file: images/logo.png");
    }

    @Test
    void shouldRespectRouterPrefixWithNestedRouters() throws IOException, InterruptedException {
        // Setup parent router with prefix
        Router apiRouter = new Router();

        // Setup child router
        Router productRouter = new Router();
        productRouter.get("/:id", (req, res) -> {
            res.send("Product " + req.getPathParam("id"));
        });

        // Mount child router (should respect parent's prefix)
        apiRouter.use("/products", productRouter);

        // Add to server without additional prefix
        server.use(apiRouter);

        // Test route with router's prefix
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/products/xyz123")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Product xyz123");
    }

    @Test
    void shouldHandleEmptyPathsAndTrailingSlashes() throws IOException, InterruptedException {
        // Setup routers
        Router apiRouter = new Router();
        Router rootHandler = new Router();

        // Routes with different slash patterns
        rootHandler.get("/", (req, res) -> res.send("Root"));
        rootHandler.get("/exact", (req, res) -> res.send("Exact"));
        rootHandler.get("/trailing/", (req, res) -> res.send("Trailing"));

        // Mount at empty path
        apiRouter.use(rootHandler);
        server.use("/api", apiRouter);

        // Test different path patterns
        HttpClient client = HttpClient.newHttpClient();

        // Root path
        HttpResponse<String> rootResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Exact path
        HttpResponse<String> exactResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/exact")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Path with trailing slash
        HttpResponse<String> trailingResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/trailing/")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Same path without trailing slash
        HttpResponse<String> noTrailingResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/trailing")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(rootResponse.statusCode()).isEqualTo(200);
        assertThat(rootResponse.body()).isEqualTo("Root");

        assertThat(exactResponse.statusCode()).isEqualTo(200);
        assertThat(exactResponse.body()).isEqualTo("Exact");

        assertThat(trailingResponse.statusCode()).isEqualTo(200);
        assertThat(trailingResponse.body()).isEqualTo("Trailing");

        // Should match the same route with or without trailing slash
        assertThat(noTrailingResponse.statusCode()).isEqualTo(200);
        assertThat(noTrailingResponse.body()).isEqualTo("Trailing");
    }

    @Test
    void shouldHandleMultipleRouterMountsWithOverlappingPaths() throws IOException, InterruptedException {
        // Setup routers
        Router apiRouter = new Router();
        Router adminRouter = new Router();
        Router userAdminRouter = new Router();

        // Add routes with potential conflicts
        apiRouter.get("/users", (req, res) -> res.send("API Users List"));

        userAdminRouter.get("/", (req, res) -> res.send("Admin Users Dashboard"));
        userAdminRouter.get("/create", (req, res) -> res.send("Create User Form"));

        adminRouter.use("/users", userAdminRouter);

        // Mount both routers to server
        server.use("/api", apiRouter);
        server.use("/admin", adminRouter);

        // Test non-conflicting routes
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> apiResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        HttpResponse<String> adminDashboardResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        HttpResponse<String> adminCreateResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/users/create")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(apiResponse.statusCode()).isEqualTo(200);
        assertThat(apiResponse.body()).isEqualTo("API Users List");

        assertThat(adminDashboardResponse.statusCode()).isEqualTo(200);
        assertThat(adminDashboardResponse.body()).isEqualTo("Admin Users Dashboard");

        assertThat(adminCreateResponse.statusCode()).isEqualTo(200);
        assertThat(adminCreateResponse.body()).isEqualTo("Create User Form");
    }

    @Test
    void shouldHandleErrorCasesInNestedRouters() throws IOException, InterruptedException {
        // Setup routers
        Router apiRouter = new Router();
        Router usersRouter = new Router();

        // Add specific routes but no fallbacks
        usersRouter.get("/:id", (req, res) -> {
            res.send("User " + req.getPathParam("id"));
        });

        apiRouter.use("/users", usersRouter);
        server.use("/api", apiRouter);

        // Test valid route
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> validResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/123")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Test invalid routes at different nesting levels
        HttpResponse<String> invalidUserRoute = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/123/nonexistent")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        HttpResponse<String> invalidApiRoute = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/nonexistent")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(validResponse.statusCode()).isEqualTo(200);
        assertThat(validResponse.body()).isEqualTo("User 123");

        // Both invalid routes should return 404
        assertThat(invalidUserRoute.statusCode()).isEqualTo(404);
        assertThat(invalidApiRoute.statusCode()).isEqualTo(404);
    }

    @Test
    void shouldPreserveMiddlewareOrderInNestedRouters() throws IOException, InterruptedException {
        // Create a list to track middleware execution order
        List<String> executionOrder = new ArrayList<>();

        // Create middleware that records execution
        Middleware middleware1 = (req, res, next) -> {
            executionOrder.add("middleware1");
            res.setHeader("X-Order", "1");
            next.next(req, res);
        };

        Middleware middleware2 = (req, res, next) -> {
            executionOrder.add("middleware2");
            res.setHeader("X-Order", res.getHeader("X-Order") + ",2");
            next.next(req, res);
        };

        Middleware middleware3 = (req, res, next) -> {
            executionOrder.add("middleware3");
            res.setHeader("X-Order", res.getHeader("X-Order") + ",3");
            next.next(req, res);
        };

        // Setup routers with middleware
        Router apiRouter = new Router();
        apiRouter.use(middleware1);

        Router usersRouter = new Router();
        usersRouter.use(middleware2);

        // Add route with inline middleware
        usersRouter.get("/:id", middleware3, (req, res) -> {
            executionOrder.add("handler");
            res.setHeader("X-Order", res.getHeader("X-Order") + ",H");
            res.send("User " + req.getPathParam("id"));
        });

        // Mount routers
        apiRouter.use("/users", usersRouter);
        server.use("/api", apiRouter);

        // Clear execution record before test
        executionOrder.clear();

        // Test route
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/789")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Verify response
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("User 789");

        // Verify middleware execution order
        assertThat(executionOrder).containsExactly("middleware1", "middleware2", "middleware3", "handler");

        // Verify order header
        assertThat(response.headers().firstValue("X-Order")).isPresent().hasValue("1,2,3,H");
    }

    @Test
    void shouldSupportRoutingWithSamePathButDifferentMethods() throws IOException, InterruptedException {
        // Setup router
        Router apiRouter = new Router();

        // Add routes with same path but different methods
        apiRouter.get("/resource", (req, res) -> res.send("GET resource"));
        apiRouter.post("/resource", (req, res) -> res.send("POST resource"));
        apiRouter.put("/resource", (req, res) -> res.send("PUT resource"));
        apiRouter.delete("/resource", (req, res) -> res.send("DELETE resource"));

        // Mount router
        server.use("/api", apiRouter);

        // Test different HTTP methods
        HttpClient client = HttpClient.newHttpClient();

        // GET request
        HttpResponse<String> getResponse = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/resource"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // POST request
        HttpResponse<String> postResponse = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/resource"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // PUT request
        HttpResponse<String> putResponse = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/resource"))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // DELETE request
        HttpResponse<String> deleteResponse = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/resource"))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Verify responses
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isEqualTo("GET resource");

        assertThat(postResponse.statusCode()).isEqualTo(200);
        assertThat(postResponse.body()).isEqualTo("POST resource");

        assertThat(putResponse.statusCode()).isEqualTo(200);
        assertThat(putResponse.body()).isEqualTo("PUT resource");

        assertThat(deleteResponse.statusCode()).isEqualTo(200);
        assertThat(deleteResponse.body()).isEqualTo("DELETE resource");
    }

    @Test
    void shouldHandleRequestsInCorrectOrderWithMultipleMatchingRoutes() throws IOException, InterruptedException {
        // Setup router with potentially conflicting routes
        Router apiRouter = new Router();

        // Add specific route
        apiRouter.get("/users/admin", (req, res) -> {
            res.send("Admin user");
        });

        // Add parameterized route that could match the same path
        apiRouter.get("/users/:id", (req, res) -> {
            res.send("User " + req.getPathParam("id"));
        });

        // Add wildcard route that could also match
        apiRouter.get("/users/*", (req, res) -> {
            res.send("Wildcard match: " + req.getPathParam("wildcard"));
        });

        // Mount router
        server.use("/api", apiRouter);

        // Test specific route - should match the first handler
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> specificResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/admin")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Test parameterized route - should match the second handler
        HttpResponse<String> paramResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/123")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Test deeper path - should match the wildcard handler
        HttpResponse<String> wildcardResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users/profile/settings")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Verify responses
        assertThat(specificResponse.statusCode()).isEqualTo(200);
        assertThat(specificResponse.body()).isEqualTo("Admin user");

        assertThat(paramResponse.statusCode()).isEqualTo(200);
        assertThat(paramResponse.body()).isEqualTo("User 123");

        assertThat(wildcardResponse.statusCode()).isEqualTo(200);
        assertThat(wildcardResponse.body()).startsWith("Wildcard match:");
    }
}