package com.cabin.express.server;

import com.cabin.express.router.Router;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ServerGracefulShutdownTests {

    @Test
    void testGracefulShutdownWaitsForRequestsToComplete() throws Exception {
        // Use dynamic port allocation
        int port = ServerTestUtil.findAvailablePort();

        CabinServer server = new ServerBuilder()
                .setPort(port)
                .build();

        Router router = new Router();

        // Add a slow endpoint that takes 3 seconds to complete
        router.get("/slow", (req, res) -> {
            try {
                System.out.println("[" + Thread.currentThread().getName() + "] Processing slow request - start");
                Thread.sleep(3000); // Simulate slow processing
                res.writeBody("Slow response completed");
                res.send();
                System.out.println("[" + Thread.currentThread().getName() + "] Processing slow request - end");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                res.setStatusCode(500);
                res.writeBody("Request interrupted");
                res.send();
            }
        });

        // Add a fast endpoint for comparison
        router.get("/fast", (req, res) -> {
            res.writeBody("Fast response");
            res.send();
        });

        server.use(router);

        // Start server in background
        Thread serverThread = ServerTestUtil.startServerInBackground(server);
        String baseUrl = "http://localhost:" + port;
        System.out.println("Server started at " + baseUrl);

        // Wait for server to be ready
        boolean isReady = ServerTestUtil.waitForServerReady(baseUrl, "/fast", 5000);
        assertThat(isReady).isTrue();

        // Test 1: Make a fast request to verify server is working
        System.out.println("\n=== Testing fast endpoint ===");
        String fastResponse = ServerTestUtil.makeHttpRequest(baseUrl + "/fast");
        assertThat(fastResponse).contains("Fast response");
        System.out.println("Fast request completed successfully");

        // Test 2: Start multiple slow requests concurrently and then stop server
        System.out.println("\n=== Starting slow requests and testing graceful shutdown ===");

        int numSlowRequests = 3;
        CompletableFuture<String>[] slowRequests = new CompletableFuture[numSlowRequests];

        // Start multiple slow requests concurrently
        for (int i = 0; i < numSlowRequests; i++) {
            final int requestId = i + 1;
            slowRequests[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    System.out.println("Starting slow request " + requestId);
                    String result = ServerTestUtil.makeHttpRequest(baseUrl + "/slow");
                    System.out.println("Slow request " + requestId + " completed");
                    return result;
                } catch (Exception e) {
                    System.out.println("Slow request " + requestId + " failed: " + e.getMessage());
                    return "ERROR: " + e.getMessage();
                }
            });
        }

        // Give the requests a moment to start processing
        Thread.sleep(500);

        // Now initiate graceful shutdown while requests are still processing
        System.out.println("\n=== Initiating graceful shutdown ===");
        long shutdownStart = System.currentTimeMillis();

        CompletableFuture<Boolean> shutdownFuture = CompletableFuture.supplyAsync(() -> {
            boolean stopped = server.stop(10000); // 10 second timeout
            long shutdownDuration = System.currentTimeMillis() - shutdownStart;
            System.out.println("Server shutdown completed in " + shutdownDuration + "ms, success: " + stopped);
            return stopped;
        });

        // Test 3: Try to make a new request after shutdown initiated (should fail)
        Thread.sleep(100); // Give shutdown a moment to start
        System.out.println("\n=== Testing new request rejection during shutdown ===");
        try {
            String rejectedResponse = ServerTestUtil.makeHttpRequest(baseUrl + "/fast");
            System.out.println("Unexpected: New request succeeded during shutdown: " + rejectedResponse);
        } catch (Exception e) {
            System.out.println("Expected: New request rejected during shutdown - " + e.getMessage());
        }

        // Wait for all slow requests to complete
        System.out.println("\n=== Waiting for slow requests to complete ===");
        for (int i = 0; i < numSlowRequests; i++) {
            try {
                String result = slowRequests[i].get(15, TimeUnit.SECONDS);
                System.out.println("Slow request " + (i + 1) + " result: " +
                        (result.contains("Slow response completed") ? "SUCCESS" : "FAILED - " + result));
                assertThat(result).contains("Slow response completed");
            } catch (TimeoutException e) {
                System.out.println("Slow request " + (i + 1) + " timed out");
                assertThat(false).withFailMessage("Slow request should not timeout during graceful shutdown").isTrue();
            }
        }

        // Wait for shutdown to complete
        System.out.println("\n=== Waiting for shutdown to complete ===");
        boolean shutdownSuccess = shutdownFuture.get(15, TimeUnit.SECONDS);
        long totalShutdownTime = System.currentTimeMillis() - shutdownStart;

        System.out.println("Total shutdown time: " + totalShutdownTime + "ms");
        System.out.println("Shutdown successful: " + shutdownSuccess);

        // Verify shutdown was successful and took reasonable time
        assertThat(shutdownSuccess).isTrue();
        assertThat(totalShutdownTime).isGreaterThan(2500); // Should wait for slow requests (3 seconds)
        assertThat(totalShutdownTime).isLessThan(8000);    // But not too long

        // Clean up
        try {
            serverThread.interrupt();
            serverThread.join(1000);
        } catch (Exception e) {
            System.out.println("Server thread cleanup: " + e.getMessage());
        }

        System.out.println("\n=== Graceful shutdown test completed successfully ===");
    }
}
