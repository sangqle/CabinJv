package com.cabin.express.util;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.server.CabinServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utility for managing server lifecycle in tests
 */
public class ServerTestUtil {
    
    /**
     * Find an available port for testing
     * 
     * @return An available port number
     * @throws IOException if unable to find an available port
     */
    public static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
    
    /**
     * Start a server in a background thread with a dynamic port
     *
     * @return Object array containing [CabinServer server, int port, Thread serverThread]
     * @throws IOException if an I/O error occurs
     */
    public static Object[] startServerWithDynamicPort() throws IOException {
        int port = findAvailablePort();
        CabinServer server = new com.cabin.express.server.ServerBuilder()
                .setPort(port)
                .build();
        Thread thread = startServerInBackground(server);
        return new Object[] { server, port, thread };
    }
    
    /**
     * Start a server in a background thread
     *
     * @param server The server to start
     * @return A thread running the server
     */
    public static Thread startServerInBackground(CabinServer server) {
        // Create a latch to ensure server starts before test runs
        CountDownLatch startLatch = new CountDownLatch(1);
        
        Thread serverThread = new Thread(() -> {
            try {
                // Signal that server thread has started
                startLatch.countDown();
                
                // Start the server (this blocks until server stops)
                server.start();
            } catch (IOException e) {
                CabinLogger.error("Error starting server: " + e.getMessage(), e);
            }
        });
        
        // Make thread a daemon so it doesn't prevent JVM shutdown
        serverThread.setDaemon(true);
        
        // Start the server thread
        serverThread.start();
        
        // Wait for thread to start
        try {
            if (!startLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Server thread failed to start in time");
            }
            
            // Give the server time to initialize
            TimeUnit.MILLISECONDS.sleep(500);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for server to start", e);
        }
        
        return serverThread;
    }
    
    /**
     * Wait for a server to be ready by checking if a test endpoint is responsive
     *
     * @param baseUrl The base URL of the server
     * @param testPath A path to check for readiness
     * @param timeoutMs Timeout in milliseconds
     * @return true if server is ready, false otherwise
     */
    public static boolean waitForServerReady(String baseUrl, String testPath, long timeoutMs) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + testPath))
                .GET()
                .build();
        
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 500) {
                    return true;
                }
            } catch (Exception e) {
                // Ignore exceptions while waiting for server to start
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Stop a server with a timeout
     *
     * @param server The server to stop
     * @param timeoutMs Timeout in milliseconds
     * @return true if server stopped successfully, false otherwise
     */
    public static boolean stopServer(CabinServer server, long timeoutMs) {
        if (server == null) {
            return true;
        }
        
        return server.stop(timeoutMs);
    }
}