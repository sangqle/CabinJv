package com.cabin.express.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.cabin.express.interfaces.ServerLifecycleCallback;
import com.cabin.express.middleware.GzipMiddleware;
import com.cabin.express.middleware.StaticMiddleware;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

/**
 * Dedicated benchmark server with optimized endpoints for performance testing
 */
public class BenchmarkServer {

    private static final int DEFAULT_PORT = 8080;
    private static final Gson gson = new Gson();

    // In-memory data store for benchmark tests
    private static final Map<String, String> dataStore = new ConcurrentHashMap<>();
    private static final AtomicLong requestCounter = new AtomicLong(0);
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public static void startBenchmarkServer() throws IOException {
        System.out.println("Starting CabinServer Benchmark...");
        // Create optimized server for benchmarking
        CabinServer server = new ServerBuilder()
                .setPort(DEFAULT_PORT)
                .setDefaultPoolSize(50)  // Higher thread pool for benchmark
                .setMaxPoolSize(200)     // Allow scaling under load
                .build();

        // Setup static file serving
        setupStaticFileServing(server);

        // Setup API routes
        setupApiRoutes(server);

        // Setup health and monitoring endpoints
        setupMonitoringEndpoints(server);

        // Start server
        server.use(new GzipMiddleware());
        server.start(new BenchmarkServerCallback());


        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down benchmark server...");
            server.stop(5000);
        }));

        // Keep the main thread alive - THIS IS THE FIX
        try {
            // Keep the main thread running
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Server interrupted, shutting down...");
            server.stop(5000);
        }
    }

    private static void setupStaticFileServing(CabinServer server) throws IOException {
        // Create a temporary static directory for testing
        Path staticDir = createTestStaticFiles();

        // Add static middleware with exclusions for API routes
        StaticMiddleware staticMiddleware = new StaticMiddleware(staticDir.toString(), "/static")
                .excludePrefixes("/api", "/health", "/metrics");

        server.use(staticMiddleware);

        // Also serve some files at root level for simple benchmarks
        StaticMiddleware rootStatic = new StaticMiddleware(staticDir.toString(), "/")
                .excludePrefixes("/api", "/health", "/metrics", "/static");

        server.use(rootStatic);
    }

    private static Path createTestStaticFiles() throws IOException {
        Path staticDir = Paths.get("benchmark-static");
        if (!Files.exists(staticDir)) {
            Files.createDirectories(staticDir);
        }

        // Create test files of various sizes
        createTestFile(staticDir, "index.html", generateHtmlContent("Benchmark Test Page", 1024));
        createTestFile(staticDir, "test.txt", "Hello from CabinServer benchmark!\n".repeat(10));
        createTestFile(staticDir, "small.json", generateJsonContent(100));
        createTestFile(staticDir, "medium.json", generateJsonContent(1000));
        createTestFile(staticDir, "large.json", generateJsonContent(10000));

        // Create CSS subdirectory
        Path cssDir = staticDir.resolve("css");
        if (!Files.exists(cssDir)) {
            Files.createDirectories(cssDir);
        }
        createTestFile(cssDir, "style.css", generateCssContent());

        return staticDir;
    }

    private static void createTestFile(Path dir, String filename, String content) throws IOException {
        Path filePath = dir.resolve(filename);
        if (!Files.exists(filePath)) {
            Files.writeString(filePath, content);
        }
    }

    private static String generateHtmlContent(String title, int size) {
        StringBuilder content = new StringBuilder();
        content.append("<!DOCTYPE html>\n<html>\n<head>\n<title>").append(title).append("</title>\n</head>\n<body>\n");
        content.append("<h1>").append(title).append("</h1>\n");
        content.append("<p>Generated content for benchmark testing.</p>\n");

        // Fill to approximate size
        String filler = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ";
        while (content.length() < size - 50) {
            content.append("<p>").append(filler).append("</p>\n");
        }

        content.append("</body>\n</html>");
        return content.toString();
    }

    private static String generateJsonContent(int size) {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", Instant.now().toString());
        json.addProperty("type", "benchmark-data");

        // Add data to reach approximate size
        StringBuilder data = new StringBuilder();
        String filler = "sample-data-";
        while (data.length() < size) {
            data.append(filler).append(UUID.randomUUID().toString());
        }

        json.addProperty("data", data.toString());
        return gson.toJson(json);
    }

    private static String generateCssContent() {
        return """
               body {
                   font-family: Arial, sans-serif;
                   margin: 0;
                   padding: 20px;
                   background-color: #f5f5f5;
               }
               
               .container {
                   max-width: 1200px;
                   margin: 0 auto;
                   background: white;
                   padding: 20px;
                   border-radius: 8px;
                   box-shadow: 0 2px 4px rgba(0,0,0,0.1);
               }
               
               h1 { color: #333; }
               p { line-height: 1.6; }
               """;
    }

    private static void setupApiRoutes(CabinServer server) {
        Router apiRouter = new Router();

        // Basic CRUD operations for data store
        setupDataStoreRoutes(apiRouter);

        // Performance testing endpoints
        setupPerformanceRoutes(apiRouter);

        // Utility endpoints
        setupUtilityRoutes(apiRouter);

        server.use("/api", apiRouter);
    }

    private static void setupDataStoreRoutes(Router router) {
        // ADD: Generic POST endpoint for /api/data (without key)
        router.post("/data", (req, res) -> {
            String body = req.getBodyAsString();
            
            // Generate a random key for the data
            String autoKey = "auto_" + System.currentTimeMillis() + "_" + 
                            new Random().nextInt(1000);
            
            dataStore.put(autoKey, body);
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("key", autoKey);
            response.addProperty("operation", "create");
            response.addProperty("timestamp", System.currentTimeMillis());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        router.post("/data/:key", (req, res) -> {
            String key = req.getPathParam("key");
            String value = req.getBodyAsString();

            dataStore.put(key, value);
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("key", key);
            response.addProperty("operation", "create/update");
            response.addProperty("timestamp", System.currentTimeMillis());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });
    
        // ... rest of existing endpoints

        // Read data
        router.get("/data/:key", (req, res) -> {
            String key = req.getPathParam("key");
            String value = dataStore.get(key);
            requestCounter.incrementAndGet();

            res.setHeader("Content-Type", "application/json");

            if (value != null) {
                JsonObject response = new JsonObject();
                response.addProperty("key", key);
                response.addProperty("value", value);
                response.addProperty("found", true);
                res.send(response);
            } else {
                res.setStatusCode(404);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Key not found");
                error.addProperty("key", key);
                error.addProperty("found", false);
                res.send(error);
            }
        });

        // Delete data
        router.delete("/data/:key", (req, res) -> {
            String key = req.getPathParam("key");
            String removed = dataStore.remove(key);
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("success", removed != null);
            response.addProperty("key", key);
            response.addProperty("operation", "delete");
            response.addProperty("existed", removed != null);

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // List all keys
        router.get("/data", (req, res) -> {
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.add("keys", gson.toJsonTree(new ArrayList<>(dataStore.keySet())));
            response.addProperty("count", dataStore.size());
            response.addProperty("timestamp", System.currentTimeMillis());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // Batch operations
        router.post("/data/batch", (req, res) -> {
            String body = req.getBodyAsString();
            java.lang.reflect.Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> batch = gson.fromJson(body, mapType);

            dataStore.putAll(batch);
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("keysProcessed", batch.size());
            response.addProperty("operation", "batch");
            response.addProperty("timestamp", System.currentTimeMillis());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // Clear all data
        router.delete("/data", (req, res) -> {
            int clearedCount = dataStore.size();
            dataStore.clear();
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("clearedCount", clearedCount);
            response.addProperty("operation", "clear");

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });
    }

    private static void setupPerformanceRoutes(Router router) {
        // CPU-intensive endpoint
        router.get("/compute/:n", (req, res) -> {
            int n = Integer.parseInt(req.getPathParam("n"));
            long result = fibonacci(n);
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("input", n);
            response.addProperty("result", result);
            response.addProperty("operation", "fibonacci");

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // Delay simulation endpoint
        router.get("/delay/:ms", (req, res) -> {
            int delayMs = Integer.parseInt(req.getPathParam("ms"));
            requestCounter.incrementAndGet();

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            JsonObject response = new JsonObject();
            response.addProperty("delayed", delayMs);
            response.addProperty("operation", "delay");
            response.addProperty("timestamp", System.currentTimeMillis());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // Echo endpoint
        router.get("/echo/:message", (req, res) -> {
            String message = req.getPathParam("message");
            requestCounter.incrementAndGet();

            res.setHeader("Content-Type", "text/plain");
            res.send(message);
        });

        // JSON echo endpoint
        router.post("/echo", (req, res) -> {
            String body = req.getBodyAsString();
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("echo", body);
            response.addProperty("timestamp", System.currentTimeMillis());
            response.addProperty("length", body.length());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // Variable response size endpoint
        router.get("/payload/:size", (req, res) -> {
            int size = Integer.parseInt(req.getPathParam("size"));
            requestCounter.incrementAndGet();

            // Generate payload of specified size
            StringBuilder payload = new StringBuilder();
            String unit = "A";
            while (payload.length() < size) {
                payload.append(unit);
            }

            JsonObject response = new JsonObject();
            response.addProperty("requestedSize", size);
            response.addProperty("actualSize", payload.length());
            response.addProperty("data", payload.toString());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });
    }

    private static void setupUtilityRoutes(Router router) {
        // Test endpoint
        router.get("/test", (req, res) -> {
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("message", "Test endpoint working");
            response.addProperty("timestamp", System.currentTimeMillis());
            response.addProperty("server", "CabinServer");

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // Request info endpoint
        router.get("/request-info", (req, res) -> {
            requestCounter.incrementAndGet();

            JsonObject response = new JsonObject();
            response.addProperty("method", req.getMethod());
            response.addProperty("path", req.getPath());
            response.addProperty("query", req.getQueryString());
            response.add("headers", gson.toJsonTree(req.getHeaders()));

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        // Random data generator
        router.get("/random/:type/:size", (req, res) -> {
            String type = req.getPathParam("type");
            int size = Integer.parseInt(req.getPathParam("size"));
            requestCounter.incrementAndGet();

            String data;
            switch (type.toLowerCase()) {
                case "string":
                    data = generateRandomString(size);
                    break;
                case "json":
                    data = generateRandomJson(size);
                    break;
                case "numbers":
                    data = generateRandomNumbers(size);
                    break;
                default:
                    data = "Unknown type: " + type;
            }

            res.setHeader("Content-Type", "application/json");
            JsonObject response = new JsonObject();
            response.addProperty("type", type);
            response.addProperty("requestedSize", size);
            response.addProperty("data", data);
            res.send(response);
        });
    }

    private static void setupMonitoringEndpoints(CabinServer server) {
        Router monitoringRouter = new Router();

        // Health check
        monitoringRouter.get("/health", (req, res) -> {
            JsonObject health = new JsonObject();
            health.addProperty("status", "UP");
            health.addProperty("timestamp", System.currentTimeMillis());
            health.addProperty("uptime", System.currentTimeMillis() - startTime.get());
            health.addProperty("server", "CabinServer");

            res.setHeader("Content-Type", "application/json");
            res.send(health);
        });

        // Metrics endpoint
        monitoringRouter.get("/metrics", (req, res) -> {
            long uptime = System.currentTimeMillis() - startTime.get();
            long requests = requestCounter.get();
            double requestsPerSecond = uptime > 0 ? (double) requests / (uptime / 1000.0) : 0;

            JsonObject metrics = new JsonObject();
            metrics.addProperty("totalRequests", requests);
            metrics.addProperty("uptime", uptime);
            metrics.addProperty("requestsPerSecond", requestsPerSecond);
            metrics.addProperty("dataStoreSize", dataStore.size());
            metrics.addProperty("timestamp", System.currentTimeMillis());

            // JVM metrics
            Runtime runtime = Runtime.getRuntime();
            JsonObject jvm = new JsonObject();
            jvm.addProperty("totalMemory", runtime.totalMemory());
            jvm.addProperty("freeMemory", runtime.freeMemory());
            jvm.addProperty("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            jvm.addProperty("maxMemory", runtime.maxMemory());
            jvm.addProperty("availableProcessors", runtime.availableProcessors());

            metrics.add("jvm", jvm);

            res.setHeader("Content-Type", "application/json");
            res.send(metrics);
        });

        // Reset metrics
        monitoringRouter.post("/metrics/reset", (req, res) -> {
            requestCounter.set(0);
            startTime.set(System.currentTimeMillis());

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Metrics reset");
            response.addProperty("timestamp", System.currentTimeMillis());

            res.setHeader("Content-Type", "application/json");
            res.send(response);
        });

        server.use(monitoringRouter);
    }

    // Helper methods
    private static long fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    private static String generateRandomJson(int approximateSize) {
        JsonObject json = new JsonObject();
        Random random = new Random();

        int itemCount = Math.max(1, approximateSize / 50);
        for (int i = 0; i < itemCount; i++) {
            json.addProperty("key" + i, "value" + random.nextInt(10000));
        }

        return gson.toJson(json);
    }

    private static String generateRandomNumbers(int count) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append(random.nextInt(1000));
        }

        return sb.toString();
    }

    // Server lifecycle callback
    private static class BenchmarkServerCallback implements ServerLifecycleCallback {

        @Override
        public void onServerInitialized(int port) {
            System.out.println("═══════════════════════════════════════════");
            System.out.println("    🚀 CabinServer Benchmark Server Started");
            System.out.println("═══════════════════════════════════════════");
            System.out.println("Port: " + port);
            System.out.println("Base URL: http://localhost:" + port);
            System.out.println();
            System.out.println("Available Endpoints:");
            System.out.println("  Health:     GET  /health");
            System.out.println("  Metrics:    GET  /metrics");
            System.out.println("  Test:       GET  /api/test");
            System.out.println("  Echo:       GET  /api/echo/{message}");
            System.out.println("  Data CRUD:  GET|POST|DELETE /api/data[/{key}]");
            System.out.println("  Compute:    GET  /api/compute/{n}");
            System.out.println("  Delay:      GET  /api/delay/{ms}");
            System.out.println("  Payload:    GET  /api/payload/{size}");
            System.out.println("  Static:     GET  /static/* or /*");
            System.out.println();
            System.out.println("Ready for benchmarking! 🎯");
            System.out.println("═══════════════════════════════════════════");
        }

        @Override
        public void onServerStopped() {
            System.out.println("\n📊 Final Statistics:");
            System.out.println("Total Requests Processed: " + requestCounter.get());
            System.out.println("Data Store Entries: " + dataStore.size());
            System.out.println("Server stopped successfully. ✅");
        }

        @Override
        public void onServerFailed(Exception e) {
            System.err.println("❌ Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}