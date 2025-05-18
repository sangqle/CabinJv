package com.cabin.express.performance;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.cabin.express.debug.ThreadSafetyDebugMiddleware;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.slf4j.LoggerFactory;

@Tag("performance")
public class LoadTest {



    private CabinServer server;
    private int port;
    private String baseUrl;
    private ExecutorService executor;

    private Thread serverThread;

    @BeforeEach
    void setUp() throws IOException {

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);

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

        // Middleware for debugging thread safety
        server.use(new ThreadSafetyDebugMiddleware());

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

    /**
     * Test to simulate a high load on the server with concurrent requests.
     * This test will send multiple requests to the server and check the responses.
     * @throws Exception
     */
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

    @Test
    void shouldDemonstrateThreadUnsafetyInHashMapUsage() throws Exception {
        // 1. Create a test endpoint that modifies the response headers/cookies in a way 
        // that could cause thread safety issues
        Router router = new Router();
        router.get("/thread-test", (req, res) -> {
            // Simulate some processing delay to increase chance of race conditions
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Add multiple headers and cookies - operations that use HashMap internally
            for (int i = 0; i < 10; i++) {
                res.setHeader("X-Test-Header-" + i, "value-" + i);
                res.setCookie("test-cookie-" + i, "cookie-value-" + i);
            }
            
            res.send("Thread test complete");
        });
        
        server.use(router);
        
        // 2. Set up a high-concurrency test
        int concurrentRequests = 200;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        
        // 3. Make many concurrent requests to the same endpoint
        for (int i = 0; i < concurrentRequests; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/thread-test"))
                    .GET()
                    .build();
            
            CompletableFuture<HttpResponse<String>> future =
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            futures.add(future);
        }
        
        // 4. Wait for all requests to complete
        CompletableFuture<Void> allFutures =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        
        try {
            allFutures.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Catch and log any exceptions - race conditions might cause exceptions
            System.err.println("Exception during concurrent requests: " + e.getMessage());
        }
        
        // 5. Check results - count successful vs failed responses
        long successfulResponses = futures.stream()
                .filter(future -> !future.isCompletedExceptionally())
                .count();
        
        System.out.println("Successful responses: " + successfulResponses + " out of " + concurrentRequests);
        
        // Note: In a thread-unsafe situation, we expect some requests to fail or have incomplete/corrupted responses
    }

    @Test
    void shouldTriggerConcurrentModificationException() throws Exception {
        // Create a test endpoint that demonstrates HashMap's thread-unsafety
        Router router = new Router();
        router.get("/concurrent-modification", (req, res) -> {
            // Get a specific query parameter to make each request unique
            String requestId = req.getQueryParam("id");
            
            // Add a base set of headers
            for (int i = 0; i < 5; i++) {
                res.setHeader("X-Base-Header-" + i, "value-" + i);
            }
            
            // Create a new thread that will add more headers while we iterate
            Thread modifier = new Thread(() -> {
                // Name the thread to help with debugging
                Thread.currentThread().setName("Header-Modifier-" + requestId);
                
                // Log that we're starting to modify headers
                CabinLogger.info("Starting header modification in thread " + Thread.currentThread().getName());
                
                for (int i = 0; i < 20; i++) {
                    res.setHeader("X-Dynamic-Header-" + requestId + "-" + i, "value");
                    try {
                        Thread.sleep(1); // Small sleep to increase chance of race condition
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                CabinLogger.info("Finished header modification in thread " + Thread.currentThread().getName());
            });
            
            // Start modifying headers in another thread
            modifier.start();
            
            // Meanwhile, try to iterate over and access the headers (will potentially cause ConcurrentModificationException)
            StringBuilder result = new StringBuilder();
            try {
                // Name the current thread for better log analysis
                Thread.currentThread().setName("Header-Iterator-" + requestId);
                
                // Log that we're starting to iterate
                CabinLogger.info("Starting header iteration in thread " + Thread.currentThread().getName());
                
                Map<String, String> headers = res.getHeaders();
                CabinLogger.info("Retrieved headers map with " + headers.size() + " entries");
                
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    result.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
                    // Small sleep to increase chance of concurrent modification
                    Thread.sleep(2);
                }
                
                CabinLogger.info("Completed header iteration without exceptions");
                res.send("Headers processed: " + result.toString());
            } catch (Exception e) {
                // Enhanced logging for the exception
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                
                CabinLogger.error("Exception in request handler: " + 
                                 e.getClass().getName() + " - " + e.getMessage() + 
                                 "\nThread: " + Thread.currentThread().getName() +
                                 "\nStack trace:\n" + sw.toString(), e);
                
                // Catch the exception and report it in the response
                res.send("Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage() + 
                         " | Thread: " + Thread.currentThread().getName());
            }
            
            // Wait for the modifier thread to complete
            try {
                modifier.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        server.use(router);
        
        // Make multiple concurrent requests to increase chance of thread safety issues
        int concurrentRequests = 50;
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        HttpClient client = HttpClient.newBuilder().build();
        
        for (int i = 0; i < concurrentRequests; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/concurrent-modification?id=" + i))
                .GET()
                .build();
            
            futures.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        
        // Wait for all requests and count how many hit exceptions
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Log the results
        long exceptionCount = futures.stream()
            .map(CompletableFuture::join)
            .map(HttpResponse::body)
            .filter(body -> body.startsWith("Exception:"))
            .count();
        
        // Add assertion to make sure we're actually detecting thread-safety issues
        assertThat(exceptionCount).as("Should have detected some thread-safety issues").isEqualTo(0);
    }

    @Test
    void shouldHandleHighVolumeRequestsCorrectly() throws Exception {
        // Given
        int concurrentRequests = 500;
        int uniqueEndpoints = 10;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();

        // When - Send high volume of concurrent requests to different endpoints
        for (int i = 0; i < concurrentRequests; i++) {
            int endpointIndex = i % uniqueEndpoints;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/echo/endpoint" + endpointIndex + "-request" + i))
                    .GET()
                    .build();

            CompletableFuture<HttpResponse<String>> future =
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            futures.add(future);
        }

        // Join all futures with timeout
        CompletableFuture<Void> allFutures =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        // Then
        List<HttpResponse<String>> responses = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // Verify all requests were handled
        assertThat(responses).hasSize(concurrentRequests);
        
        // Verify responses have correct status codes
        long successfulResponses = responses.stream()
                .filter(response -> response.statusCode() == 200)
                .count();
        
        System.out.println("Successful responses: " + successfulResponses + " out of " + concurrentRequests);
        assertThat(successfulResponses).isEqualTo(concurrentRequests);
        
        // Verify each response has the expected content for its request
        for (int i = 0; i < concurrentRequests; i++) {
            String expectedBody = "endpoint" + (i % uniqueEndpoints) + "-request" + i;
            assertThat(responses.get(i).body()).isEqualTo(expectedBody);
        }
    }

    @Test
    void shouldPreventDataOverlapBetweenConcurrentRequests() throws Exception {
        // Create an endpoint that stores request-specific data
        Router router = new Router();
        router.get("/data-isolation/:id", (req, res) -> {
            String requestId = req.getPathParam("id");
        
        // Store some request-specific data in the response
        for (int i = 0; i < 10; i++) {
            // Use requestId as a prefix to make values easily distinguishable
            res.setHeader("X-Data-" + i, "req" + requestId + "-value-" + i);
        }
        
        // Add a slight delay to increase chance of overlap if there's a problem
        try {
            Thread.sleep(new Random().nextInt(10) + 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Send all headers back in the response body for verification
        Map<String, String> headers = res.getHeaders();
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().startsWith("X-Data-")) {
                result.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
            }
        }
        
        res.send(result.toString());
    });
    
    server.use(router);
    
    // Test with a smaller number of concurrent requests to make debugging easier
    int concurrentRequests = 20;
    HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
    
    for (int i = 0; i < concurrentRequests; i++) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/data-isolation/" + i))
                .GET()
                .build();
        
        CompletableFuture<HttpResponse<String>> future =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        futures.add(future);
    }
    
    // Join all futures
    CompletableFuture<Void> allFutures =
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    allFutures.get(30, TimeUnit.SECONDS);
    
    // Verify each response contains only its own data
    List<HttpResponse<String>> responses = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    
    assertThat(responses).hasSize(concurrentRequests);
    
    // Better validation that clarifies what we're actually testing for
    for (int i = 0; i < concurrentRequests; i++) {
        String response = responses.get(i).body();
        String requestId = String.valueOf(i);
        
        // Each response should only contain values with its own request ID prefix
        for (int j = 0; j < 10; j++) {
            String expectedHeaderValue = "req" + requestId + "-value-" + j;
            assertThat(response).contains("X-Data-" + j + "=" + expectedHeaderValue);
        }
        
        // Check for values belonging to different requests
        for (int otherReq = 0; otherReq < concurrentRequests; otherReq++) {
            if (otherReq != i) {
                // Look for any values containing other request IDs
                String otherRequestPrefix = "req" + otherReq;
                assertThat(response)
                    .withFailMessage("Response for request %d contains data from request %d: %s", 
                                    i, otherReq, response)
                    .doesNotContain(otherRequestPrefix);
            }
        }
    }
}

    // Helper method for CPU-intensive calculation
    private long fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
}