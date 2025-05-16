package com.cabin.express.performance;

import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("performance")
public class LoadTest {

    private CabinServer server;
    private int port;
    private String baseUrl;
    private ExecutorService executor;

    private Thread serverThread;

    @BeforeEach
    void setUp() throws IOException {
        // Setup thread pool for parallel requests
        executor = Executors.newFixedThreadPool(50);
        port = ServerTestUtil.findAvailablePort();
        baseUrl = "http://localhost:" + port;

        // Setup test server
        server = new ServerBuilder()
                .setPort(port)
                .setDefaultPoolSize(20)
                .setMaxPoolSize(50)
                .build();

        Router router = new Router();

        // Simple echo endpoint
        router.get("/echo/:message", (req, res) -> {
            String message = req.getPathParam("message");
            res.send(message);
        });

        // CPU-intensive endpoint
        router.get("/compute/:n", (req, res) -> {
            int n = Integer.parseInt(req.getPathParam("n"));
            long result = fibonacci(n);
            res.send(String.valueOf(result));
        });

        // Response delay endpoint
        router.get("/delay/:ms", (req, res) -> {
            int delayMs = Integer.parseInt(req.getPathParam("ms"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            res.send("Delayed " + delayMs + "ms");
        });

        server.use(router);

        // Start server in background thread
        serverThread = ServerTestUtil.startServerInBackground(server);

        // Verify server is ready
        boolean isReady = ServerTestUtil.waitForServerReady(baseUrl, "/echo/test", 5000);
        if (!isReady) {
            fail("Server failed to start in time");
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            boolean stopped = ServerTestUtil.stopServer(server, 10000); // Longer timeout for load tests
            assertThat(stopped).withFailMessage("Server failed to stop cleanly").isTrue();
        }

        if (executor != null) {
            executor.shutdownNow();
            try {
                boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
                if (!terminated) {
                    System.err.println("Warning: Executor did not terminate in the allowed time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void shouldHandleConcurrentRequests() throws Exception {
        // Given
        int concurrentRequests = 100;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();

        // When - Send concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/echo/hello" + i))
                    .GET()
                    .build();

            CompletableFuture<HttpResponse<String>> future =
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            futures.add(future);
        }

        // Join all futures
        CompletableFuture<Void> allFutures =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.join();

        // Then
        List<HttpResponse<String>> responses = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        assertThat(responses).hasSize(concurrentRequests);
        assertThat(responses).allMatch(response -> response.statusCode() == 200);

        // Verify each response has the correct body
        for (int i = 0; i < concurrentRequests; i++) {
            assertThat(responses.get(i).body()).isEqualTo("hello" + i);
        }
    }

    @Test
    void shouldHandleMixedWorkload() throws Exception {
        // Given
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        int totalRequests = 60;

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();

        // When - Send mixed workload (CPU-intensive, IO-bound, and quick responses)
        for (int i = 0; i < totalRequests; i++) {
            final HttpRequest request;

            // Every third request is CPU intensive, every third is delayed I/O, rest are quick
            if (i % 3 == 0) {
                // CPU intensive - calculate fibonacci
                request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/compute/30"))
                        .GET()
                        .build();
            } else if (i % 3 == 1) {
                // I/O bound - artificial delay
                request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/delay/100"))
                        .GET()
                        .build();
            } else {
                // Quick response
                request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/echo/quick" + i))
                        .GET()
                        .build();
            }

            CompletableFuture<HttpResponse<String>> future =
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            futures.add(future);
        }

        // Join all futures with a timeout
        CompletableFuture<Void> allFutures =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        // Then
        List<HttpResponse<String>> responses = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        assertThat(responses).hasSize(totalRequests);
        assertThat(responses).allMatch(response -> response.statusCode() == 200);
    }

    // Helper method for CPU-intensive calculation
    private long fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
}
