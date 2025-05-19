package com.cabin.express.integration;

import com.cabin.express.interfaces.Middleware;
import com.cabin.express.middleware.StaticMiddleware;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;


public class MiddlewareIntegrationTest {

    private CabinServer server;
    private int TEST_PORT;
    private String BASE_URL;
    
    @TempDir
    Path tempDir;
    
    private Thread serverThread;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create test static files
        Files.writeString(tempDir.resolve("index.html"), "<html><body>Static Index</body></html>");
        Files.writeString(tempDir.resolve("test.txt"), "Test file content");
        
        // Use dynamic port allocation
        Object[] serverInfo = ServerTestUtil.startServerWithDynamicPort();
        server = (CabinServer) serverInfo[0];
        TEST_PORT = (int) serverInfo[1];
        serverThread = (Thread) serverInfo[2];
        BASE_URL = "http://localhost:" + TEST_PORT;
        
        // Add a simple endpoint to check server availability
        Router healthRouter = new Router();
        healthRouter.get("/health", (req, res) -> {
            res.setStatusCode(200);
            res.send("OK");
        });
        server.use(healthRouter);
        
        // Verify server is ready
        boolean isReady = ServerTestUtil.waitForServerReady(BASE_URL, "/health", 5000);
        if (!isReady) {
            fail("Server failed to start in time");
        }
    }
    
    @AfterEach
    void tearDown() {
        if (server != null) {
            boolean stopped = ServerTestUtil.stopServer(server, 5000);
            assertThat(stopped).withFailMessage("Server failed to stop cleanly").isTrue();
        }
    }
    
    @Test
    void shouldApplyCustomMiddleware() throws IOException, InterruptedException {
        // Given
        AtomicBoolean middlewareExecuted = new AtomicBoolean(false);
        
        Middleware customMiddleware = (req, res, next) -> {
            middlewareExecuted.set(true);
            res.setHeader("X-Custom-Header", "TestValue");
            next.next(req, res);
        };
        
        Router router = new Router();
        router.get("/test", (req, res) -> {
            res.writeBody("Test Route");
            res.send();
        });
        
        server.use(customMiddleware);
        server.use(router);
        
        // When
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/test"))
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Custom-Header")).isPresent().hasValue("TestValue");
        assertThat(response.body()).isEqualTo("Test Route");
        assertThat(middlewareExecuted.get()).isTrue();
    }
    
    @Test
    void shouldServeStaticFiles() throws IOException, InterruptedException {
        // Given
        StaticMiddleware staticMiddleware = new StaticMiddleware(tempDir.toString(), "/static");
        
        Router apiRouter = new Router();
        apiRouter.setPrefix("/api");
        apiRouter.get("/test", (req, res) -> {
            res.writeBody("API Test");
            res.send();
        });
        
        server.use(staticMiddleware);
        server.use(apiRouter);
        
        // When - Access static file
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest staticRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/static/test.txt"))
                .GET()
                .build();
        
        HttpResponse<String> staticResponse = client.send(staticRequest, HttpResponse.BodyHandlers.ofString());
        
        // When - Access API route
        HttpRequest apiRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/test"))
                .GET()
                .build();
        
        HttpResponse<String> apiResponse = client.send(apiRequest, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(staticResponse.statusCode()).isEqualTo(200);
        assertThat(staticResponse.body()).isEqualTo("Test file content");
        
        assertThat(apiResponse.statusCode()).isEqualTo(200);
        assertThat(apiResponse.body()).isEqualTo("API Test");
    }
    
    @Test
    void shouldApplyMiddlewareChain() throws IOException, InterruptedException {
        // Given
        AtomicBoolean middleware1Executed = new AtomicBoolean(false);
        AtomicBoolean middleware2Executed = new AtomicBoolean(false);
        
        Middleware middleware1 = (req, res, next) -> {
            middleware1Executed.set(true);
            res.setHeader("X-Order", "first");
            next.next(req, res);
        };
        
        Middleware middleware2 = (req, res, next) -> {
            middleware2Executed.set(true);
            res.setHeader("X-Order", res.getHeader("X-Order") + ",second");
            next.next(req, res);
        };
        
        Router router = new Router();
        router.get("/chain", (req, res) -> {
            res.writeBody("Chain Test");
            res.send();
        });
        
        server.use(middleware1);
        server.use(middleware2);
        server.use(router);
        
        // When
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chain"))
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Order")).isPresent().hasValue("first,second");
        assertThat(middleware1Executed.get()).isTrue();
        assertThat(middleware2Executed.get()).isTrue();
    }
    
    @Test
    void shouldExcludePrefixesFromStaticMiddleware() throws IOException, InterruptedException {
        // Given
        StaticMiddleware staticMiddleware = new StaticMiddleware(tempDir.toString(), "/")
                .excludePrefixes("/api");
        
        Router apiRouter = new Router();
        apiRouter.setPrefix("/api");
        apiRouter.get("/test", (req, res) -> {
            res.writeBody("API Route");
            res.send();
        });
        
        server.use(apiRouter);
        server.use(staticMiddleware);
        
        // When - Access excluded API route
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest apiRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/test"))
                .GET()
                .build();
        
        HttpResponse<String> apiResponse = client.send(apiRequest, HttpResponse.BodyHandlers.ofString());
        
        // When - Access static file
        HttpRequest staticRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/index.html"))
                .GET()
                .build();
        
        HttpResponse<String> staticResponse = client.send(staticRequest, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(apiResponse.statusCode()).isEqualTo(200);
        assertThat(apiResponse.body()).isEqualTo("API Route");
        
        assertThat(staticResponse.statusCode()).isEqualTo(200);
        assertThat(staticResponse.body()).contains("Static Index");
    }
}