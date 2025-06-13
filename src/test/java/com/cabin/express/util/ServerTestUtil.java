package com.cabin.express.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.cabin.express.logger.CabinLogger;
import com.cabin.express.server.CabinServer;

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
        
        // Start the server non-blocking
        server.start(null);
        
        // Wait for the server to initialize
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Create a placeholder thread for API compatibility
        Thread placeholderThread = new Thread("ServerTestUtil-Placeholder");
        
        return new Object[] { server, port, placeholderThread };
    }
    
    /**
     * Start a server in a background thread
     *
     * @param server The server to start
     * @return A placeholder thread for API compatibility
     */
    public static Thread startServerInBackground(CabinServer server) throws IOException {
        // Start the server in non-blocking mode
        server.start(null);
        
        // Wait for the server to initialize
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Return a placeholder thread for API compatibility
        Thread placeholderThread = new Thread("ServerTestUtil-Placeholder");
        return placeholderThread;
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
     * Stop a server with a timeout - with improved error handling
     *
     * @param server The server to stop
     * @param timeoutMs Timeout in milliseconds
     * @return true if server stopped successfully, false otherwise
     */
    public static boolean stopServer(CabinServer server, long timeoutMs) {
        if (server == null) {
            return true;
        }
        
        // Create a separate thread to stop the server to avoid potential deadlocks
        Thread stopThread = new Thread(() -> {
            try {
                // Call server's stop method with timeout
                server.stop(timeoutMs);
            } catch (Exception e) {
                CabinLogger.error("Error stopping server: " + e.getMessage(), e);
            }
        }, "ServerStop-Thread");
        
        stopThread.setDaemon(true);
        stopThread.start();
        
        // Wait for the stop thread to complete with a timeout
        try {
            stopThread.join(timeoutMs + 1000);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CabinLogger.error("Interrupted while waiting for server to stop", e);
            return false;
        }
    }
}