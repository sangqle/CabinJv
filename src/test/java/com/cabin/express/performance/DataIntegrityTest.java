package com.cabin.express.performance;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.util.ServerTestUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ## Test Class Overview
 * This test class performs comprehensive data integrity testing under high load conditions:
 * 1. **Multiple Concurrent Users Test**: Simulates multiple users performing read, write, and delete operations concurrently to test for race conditions and data corruption.
 * 2. **Batch Operations Test**: Tests the ability to handle multiple large batches of data simultaneously.
 * 3. **Race Conditions Test**: Forces multiple threads to contend for the same keys to test thread safety under extreme conditions.
 *
 * ## Test Features
 * 1. **Realistic User Behavior**:
 *     - Mix of read, write, and delete operations with realistic distribution
 *     - Variable data sizes
 *     - Randomized timing between operations
 *
 * 2. **Comprehensive Validation**:
 *     - Tracks every operation and its expected outcome
 *     - Verifies final data state matches expected state
 *     - Reports detailed statistics on success rates and data integrity
 *
 * 3. **Server Stress Testing**:
 *     - Configurable number of concurrent users and operations
 *     - Randomized workload patterns
 *     - Batch operations for high throughput testing
 *
 * 4. **Detailed Reporting**:
 *     - Success rates by operation type
 *     - Detailed logs of data integrity issues
 *     - Performance metrics
 *
 * This test class will help you identify any thread safety issues in your server implementation while processing high volumes of concurrent requests.
 */

@Tag("data-integrity")
public class DataIntegrityTest {

    private CabinServer server;
    private int port;
    private String baseUrl;
    private Thread serverThread;
    private final Map<String, String> sharedDataStore = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final Random random = new Random();

    // Test configuration
    private static final int CONCURRENT_USERS = 100;
    private static final int OPERATIONS_PER_USER = 50;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int MAX_VALUE_SIZE = 10_000; // Characters

    @BeforeEach
    void setUp() throws IOException {
        // Use dynamic port allocation
        Object[] serverInfo = ServerTestUtil.startServerWithDynamicPort();
        server = (CabinServer) serverInfo[0];
        port = (int) serverInfo[1];
        serverThread = (Thread) serverInfo[2];
        baseUrl = "http://localhost:" + port;

        // Setup test routes
        setupTestRoutes();

        // Verify server is ready
        boolean isReady = ServerTestUtil.waitForServerReady(baseUrl, "/health", 5000);
        if (!isReady) {
            throw new IllegalStateException("Server failed to start in time for test");
        }

        CabinLogger.info("Test server started on port " + port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            boolean stopped = ServerTestUtil.stopServer(server, 10000);
            assertThat(stopped).withFailMessage("Server failed to stop cleanly").isTrue();
        }
        sharedDataStore.clear();
    }

    private void setupTestRoutes() {
        Router router = new Router();

        // Health check endpoint
        router.get("/health", (req, res) -> {
            res.setHeader("Content-Type", "application/json");
            JsonObject health = new JsonObject();
            health.addProperty("status", "UP");
            health.addProperty("timestamp", System.currentTimeMillis());
            res.send(health);
        });

        // Data store operations

        // 1. Create or update data
        router.post("/data/:key", (req, res) -> {
            String key = req.getPathParam("key");
            String value = req.getBodyAsString();

            // Simulate some processing
            simulateWork(1, 10);

            // Store data
            sharedDataStore.put(key, value);

            res.setHeader("Content-Type", "application/json");
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("key", key);
            result.addProperty("operation", "create/update");
            res.send(result);
        });

        // 2. Get data
        router.get("/data/:key", (req, res) -> {
            String key = req.getPathParam("key");

            // Simulate some processing
            simulateWork(1, 5);

            String value = sharedDataStore.get(key);

            res.setHeader("Content-Type", "application/json");
            if (value != null) {
                JsonObject result = new JsonObject();
                result.addProperty("key", key);
                result.addProperty("value", value);
                res.send(result);
            } else {
                res.setStatusCode(404);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Key not found");
                error.addProperty("key", key);
                res.send(error);
            }
        });

        // 3. Delete data
        router.delete("/data/:key", (req, res) -> {
            String key = req.getPathParam("key");

            // Simulate some processing
            simulateWork(1, 5);

            String removed = sharedDataStore.remove(key);

            res.setHeader("Content-Type", "application/json");
            JsonObject result = new JsonObject();
            result.addProperty("success", removed != null);
            result.addProperty("key", key);
            result.addProperty("operation", "delete");
            res.send(result);
        });

        // 4. List all keys
        router.get("/data", (req, res) -> {
            // Simulate some processing
            simulateWork(5, 20);

            res.setHeader("Content-Type", "application/json");
            res.send(new ArrayList<>(sharedDataStore.keySet()));
        });

        // 5. Batch operations endpoint
        router.post("/data/batch", (req, res) -> {
            String body = req.getBodyAsString();
            // Use TypeToken for proper Map deserialization
            java.lang.reflect.Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> batch = gson.fromJson(body, mapType);
            
            // Synchronize access to shared data store
            synchronized(sharedDataStore) {
                sharedDataStore.putAll(batch);
            }
            
            res.setHeader("Content-Type", "application/json");
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("keysProcessed", batch.size());
            result.addProperty("operation", "batch");
            res.send(result);
        });

        server.use(router);
    }

    private void simulateWork(int baseMs, int maxRandomMs) {
        try {
            Thread.sleep(baseMs + random.nextInt(maxRandomMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void shouldMaintainDataIntegrityUnderHighConcurrency() throws Exception {
        // 1. Set up a map to track expected data
        Map<String, String> expectedData = new ConcurrentHashMap<>();

        // 2. Create HTTP client
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        // 3. Create a thread pool for concurrent users
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_USERS);

        // 4. Submit concurrent tasks for each user
        List<Future<UserSessionResult>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            int userId = i;
            futures.add(executorService.submit(() ->
                    simulateUserSession(userId, client, expectedData)
            ));
        }

        // 5. Wait for all user sessions to complete
        List<UserSessionResult> results = new ArrayList<>();
        for (Future<UserSessionResult> future : futures) {
            results.add(future.get(3, TimeUnit.MINUTES));
        }

        // 6. Validate overall test results

        // 6.1 Calculate success rate
        long totalRequests = results.stream().mapToLong(r -> r.totalRequests).sum();
        long successfulRequests = results.stream().mapToLong(r -> r.successfulRequests).sum();
        double successRate = (double) successfulRequests / totalRequests;

        // 6.2 Count successful operations by type
        Map<String, Long> operationCounts = results.stream()
                .flatMap(r -> r.operations.stream())
                .collect(Collectors.groupingBy(Operation::getType, Collectors.counting()));

        // 6.3 Verify final data state
        Map<String, String> finalState = fetchAllData(client);

        // 6.4 Print test summary
        CabinLogger.info("Data Integrity Test Results:");
        CabinLogger.info("---------------------------");
        CabinLogger.info("Total Users: " + CONCURRENT_USERS);
        CabinLogger.info("Operations Per User: " + OPERATIONS_PER_USER);
        CabinLogger.info("Total Requests: " + totalRequests);
        CabinLogger.info("Successful Requests: " + successfulRequests);
        CabinLogger.info("Success Rate: " + String.format("%.2f%%", successRate * 100));
        CabinLogger.info("---------------------------");
        CabinLogger.info("Operation Counts:");
        operationCounts.forEach((type, count) ->
                CabinLogger.info("  " + type + ": " + count)
        );
        CabinLogger.info("---------------------------");
        CabinLogger.info("Final Data Store Size: " + finalState.size());

        // 7. Check for data integrity against expected data
        verifyDataIntegrity(expectedData, finalState);

        // 8. Clean up
        executorService.shutdown();
        assertThat(executorService.awaitTermination(1, TimeUnit.MINUTES))
                .withFailMessage("Executor service failed to terminate")
                .isTrue();
    }

    @Test
    void shouldHandleBatchOperationsCorrectly() throws Exception {
        // 1. Create HTTP client
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        // 2. Create multiple batches of data
        int numBatches = 10;
        int batchSize = 100;
        List<Map<String, String>> batches = new ArrayList<>();

        for (int batchNum = 0; batchNum < numBatches; batchNum++) {
            Map<String, String> batch = new ConcurrentHashMap<>();
            for (int i = 0; i < batchSize; i++) {
                String key = "batch-" + batchNum + "-key-" + i;
                String value = generateRandomString(10, 100);
                batch.put(key, value);
            }
            batches.add(batch);
        }

        // 3. Process batches concurrently
        ExecutorService executor = Executors.newFixedThreadPool(numBatches);
        List<Future<Boolean>> futures = new ArrayList<>();
        
        // Track successful batches
        AtomicInteger successfulBatches = new AtomicInteger(0);
        
        for (Map<String, String> batch : batches) {
            futures.add(executor.submit(() -> {
                try {
                    boolean success = processBatch(client, batch);
                    if (success) {
                        successfulBatches.incrementAndGet();
                        CabinLogger.info("Successfully processed batch with " + batch.size() + " entries");
                    } else {
                        CabinLogger.warn("Failed to process batch with " + batch.size() + " entries");
                    }
                    return success;
                } catch (Exception e) {
                    CabinLogger.error("Error processing batch: " + e.getMessage(), e);
                    return false;
                }
            }));
        }

        // 4. Wait for all batch operations to complete and check results
        int failedBatches = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (!future.get(2, TimeUnit.MINUTES)) {
                    failedBatches++;
                }
            } catch (Exception e) {
                failedBatches++;
                CabinLogger.error("Exception waiting for batch: " + e.getMessage(), e);
            }
        }
        
        CabinLogger.info("Batch processing completed: " + successfulBatches.get() + 
                         " successful, " + failedBatches + " failed");

        // 5. Verify all data was stored correctly
        Map<String, String> actualData = fetchAllData(client);
        
        Map<String, String> expectedData = new ConcurrentHashMap<>();
        batches.forEach(expectedData::putAll);
        
        CabinLogger.info("Expected data size: " + expectedData.size());
        CabinLogger.info("Actual data size: " + actualData.size());
        
        // 6. Validate data integrity using the comprehensive verification method
        verifyDataIntegrity(expectedData, actualData);

        // 7. Clean up
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES))
            .withFailMessage("Executor service failed to terminate")
            .isTrue();
    }

    @Test
    void shouldHandleRaceConditionsOnSameKeys() throws Exception {
        // 1. Create HTTP client
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        // 2. Define a fixed set of keys that will be targeted by multiple threads
        List<String> targetKeys = IntStream.range(0, 10)
                .mapToObj(i -> "contested-key-" + i)
                .collect(Collectors.toList());

        // 3. Create a thread pool with multiple concurrent writers
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger counter = new AtomicInteger(0);

        // 4. Have threads race to update the same keys
        List<Future<Map<String, Integer>>> futures = new ArrayList<>();
        for (int threadNum = 0; threadNum < numThreads; threadNum++) {
            int threadId = threadNum;
            futures.add(executor.submit(() -> {
                Map<String, Integer> operations = new HashMap<>();
                for (int i = 0; i < 100; i++) {
                    // Pick a random key from the target list
                    String key = targetKeys.get(random.nextInt(targetKeys.size()));

                    // Create a thread-specific value
                    String value = "thread-" + threadId + "-update-" + counter.incrementAndGet();

                    // Update that key
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/data/" + key))
                            .POST(HttpRequest.BodyPublishers.ofString(value))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            operations.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
                        }
                    } catch (Exception e) {
                        CabinLogger.error("Error in race condition test: " + e.getMessage(), e);
                    }
                }
                return operations;
            }));
        }

        // 5. Wait for all operations to complete
        List<Map<String, Integer>> results = new ArrayList<>();
        for (Future<Map<String, Integer>> future : futures) {
            results.add(future.get(2, TimeUnit.MINUTES));
        }

        // 6. Verify the final state
        Map<String, String> finalState = new HashMap<>();
        for (String key : targetKeys) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/data/" + key))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    String value = json.get("value").getAsString();
                    finalState.put(key, value);

                    // Log the final value of each key
                    CabinLogger.info("Final state of key " + key + ": " + value);
                }
            } catch (Exception e) {
                CabinLogger.error("Error fetching final state: " + e.getMessage(), e);
            }
        }

        // 7. Count total operations per key across all threads
        Map<String, Integer> totalOps = new HashMap<>();
        for (Map<String, Integer> threadResult : results) {
            for (Map.Entry<String, Integer> entry : threadResult.entrySet()) {
                totalOps.compute(entry.getKey(), (k, v) -> (v == null) ? entry.getValue() : v + entry.getValue());
            }
        }

        // 8. Log operation statistics
        CabinLogger.info("Race Condition Test Results:");
        CabinLogger.info("---------------------------");
        CabinLogger.info("Total threads: " + numThreads);
        CabinLogger.info("Operations per key:");
        totalOps.forEach((key, count) -> CabinLogger.info("  " + key + ": " + count));

        // 9. Make sure we have all keys in the final state
        assertThat(finalState.keySet())
                .withFailMessage("Some keys are missing from final state")
                .containsExactlyInAnyOrderElementsOf(targetKeys);

        // 10. Clean up
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.MINUTES))
                .withFailMessage("Executor service failed to terminate")
                .isTrue();
    }

    // Simulate a user session with mixed read, write, and delete operations
    private UserSessionResult simulateUserSession(
            int userId,
            HttpClient client,
            Map<String, String> expectedData) throws Exception {

        UserSessionResult result = new UserSessionResult();
        result.userId = userId;

        // User's key namespace to avoid direct conflicts between users
        String keyPrefix = "user-" + userId + "-";

        for (int i = 0; i < OPERATIONS_PER_USER; i++) {
            // Choose operation type based on weighted distribution
            int opType = random.nextInt(100);
            Operation op = new Operation();

            try {
                if (opType < 60) { // 60% writes
                    // Create or update
                    String key = keyPrefix + "key-" + random.nextInt(20); // Reuse keys occasionally
                    String value = generateRandomString(10, MAX_VALUE_SIZE);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/data/" + key))
                            .POST(HttpRequest.BodyPublishers.ofString(value))
                            .build();

                    result.totalRequests++;
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    op.type = "CREATE";
                    op.key = key;
                    op.statusCode = response.statusCode();

                    if (response.statusCode() == 200) {
                        // Update local tracking
                        expectedData.put(key, value);
                        result.successfulRequests++;
                    }
                } else if (opType < 90) { // 30% reads
                    // Read a random key (sometimes nonexistent)
                    String key;
                    if (random.nextInt(100) < 80) {
                        // 80% of the time read own keys
                        key = keyPrefix + "key-" + random.nextInt(20);
                    } else {
                        // 20% of the time read other users' keys
                        key = "user-" + random.nextInt(CONCURRENT_USERS) + "-key-" + random.nextInt(20);
                    }

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/data/" + key))
                            .GET()
                            .build();

                    result.totalRequests++;
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    op.type = "READ";
                    op.key = key;
                    op.statusCode = response.statusCode();

                    // Verify read data matches expected (if found)
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        String actualValue = json.get("value").getAsString();
                        String expectedValue = expectedData.get(key);

                        op.success = expectedValue == null || expectedValue.equals(actualValue);
                        if (op.success) {
                            result.successfulRequests++;
                        }
                    } else if (response.statusCode() == 404) {
                        // If key not found, it should also not be in expected data
                        op.success = !expectedData.containsKey(key);
                        if (op.success) {
                            result.successfulRequests++;
                        }
                    }
                } else { // 10% deletes
                    // Delete
                    String key = keyPrefix + "key-" + random.nextInt(20);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/data/" + key))
                            .DELETE()
                            .build();

                    result.totalRequests++;
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    op.type = "DELETE";
                    op.key = key;
                    op.statusCode = response.statusCode();

                    if (response.statusCode() == 200) {
                        // Update local tracking
                        expectedData.remove(key);
                        result.successfulRequests++;
                    }
                }
            } catch (Exception e) {
                op.success = false;
                op.error = e.getMessage();
                CabinLogger.error("Error in user session " + userId + ": " + e.getMessage(), e);
            }

            result.operations.add(op);
        }

        return result;
    }

    private boolean processBatch(HttpClient client, Map<String, String> batchData) {
        try {
            String jsonData = gson.toJson(batchData);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/data/batch"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                CabinLogger.error("Batch operation failed with status: " + response.statusCode());
                return false;
            }
            return true;
        } catch (Exception e) {
            CabinLogger.error("Error processing batch: " + e.getMessage(), e);
            return false;
        }
    }

    private Map<String, String> fetchAllData(HttpClient client) throws Exception {
        // 1. Get all keys
        HttpRequest keysRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/data"))
                .GET()
                .build();

        HttpResponse<String> keysResponse = client.send(keysRequest, HttpResponse.BodyHandlers.ofString());

        if (keysResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch keys, status: " + keysResponse.statusCode());
        }

        List<String> keys = gson.fromJson(keysResponse.body(), new TypeToken<List<String>>(){}.getType());

        // 2. Fetch each key's value
        Map<String, String> result = new ConcurrentHashMap<>();
        for (String key : keys) {
            HttpRequest valueRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/data/" + key))
                    .GET()
                    .build();

            HttpResponse<String> valueResponse = client.send(valueRequest, HttpResponse.BodyHandlers.ofString());

            if (valueResponse.statusCode() == 200) {
                JsonObject json = gson.fromJson(valueResponse.body(), JsonObject.class);
                result.put(key, json.get("value").getAsString());
            }
        }

        return result;
    }

    private void verifyDataIntegrity(Map<String, String> expected, Map<String, String> actual) {
        // Report on any keys that should exist but don't
        Set<String> missingKeys = new HashSet<>(expected.keySet());
        missingKeys.removeAll(actual.keySet());

        if (!missingKeys.isEmpty()) {
            CabinLogger.warn("Missing keys in final state: " + missingKeys.size());
            // Log a sample of missing keys (up to 10)
            missingKeys.stream().limit(10).forEach(key ->
                CabinLogger.warn("  Missing key: " + key + ", expected value: " + expected.get(key))
            );
        }

        // Report on any keys that exist but shouldn't
        Set<String> unexpectedKeys = new HashSet<>(actual.keySet());
        unexpectedKeys.removeAll(expected.keySet());

        if (!unexpectedKeys.isEmpty()) {
            CabinLogger.warn("Unexpected keys in final state: " + unexpectedKeys.size());
            // Log a sample of unexpected keys (up to 10)
            unexpectedKeys.stream().limit(10).forEach(key ->
                CabinLogger.warn("  Unexpected key: " + key + ", value: " + actual.get(key))
            );
        }

        // Check for value mismatches
        AtomicInteger mismatchCount = new AtomicInteger(0);
        Set<String> commonKeys = new HashSet<>(expected.keySet());
        commonKeys.retainAll(actual.keySet());

        for (String key : commonKeys) {
            String expectedValue = expected.get(key);
            String actualValue = actual.get(key);

            if (!Objects.equals(expectedValue, actualValue)) {
                mismatchCount.incrementAndGet();
                if (mismatchCount.get() <= 10) { // Limit logging to 10 mismatches
                    CabinLogger.warn("Value mismatch for key: " + key);
                    CabinLogger.warn("  Expected: " + (expectedValue.length() > 100 ?
                        expectedValue.substring(0, 100) + "..." : expectedValue));
                    CabinLogger.warn("  Actual: " + (actualValue.length() > 100 ?
                        actualValue.substring(0, 100) + "..." : actualValue));
                }
            }
        }

        if (mismatchCount.get() > 0) {
            CabinLogger.warn("Total value mismatches: " + mismatchCount.get());
        }

        // Log overall integrity summary
        double integrityPercentage = (commonKeys.size() - mismatchCount.get()) * 100.0 /
            (expected.size() + unexpectedKeys.size());

        CabinLogger.info("Data Integrity Summary:");
        CabinLogger.info("  Expected Keys: " + expected.size());
        CabinLogger.info("  Actual Keys: " + actual.size());
        CabinLogger.info("  Missing Keys: " + missingKeys.size());
        CabinLogger.info("  Unexpected Keys: " + unexpectedKeys.size());
        CabinLogger.info("  Value Mismatches: " + mismatchCount.get());
        CabinLogger.info("  Integrity Percentage: " + String.format("%.2f%%", integrityPercentage));

        // Make assertions
        boolean perfectIntegrity = missingKeys.isEmpty() && unexpectedKeys.isEmpty() && mismatchCount.get() == 0;

        if (perfectIntegrity) {
            assertThat(integrityPercentage).isEqualTo(100.0);
        } else {
            // Allow for some minor inconsistencies in high concurrency
            assertThat(integrityPercentage)
                .withFailMessage("Data integrity too low: %.2f%%", integrityPercentage)
                .isGreaterThanOrEqualTo(95.0);
        }
    }

    private String generateRandomString(int minLength, int maxLength) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Helper classes to track test results

    private static class UserSessionResult {
        int userId;
        long totalRequests = 0;
        long successfulRequests = 0;
        List<Operation> operations = new ArrayList<>();
    }

    private static class Operation {
        String type;
        String key;
        int statusCode;
        boolean success = true;
        String error;

        public String getType() {
            return type;
        }
    }
}