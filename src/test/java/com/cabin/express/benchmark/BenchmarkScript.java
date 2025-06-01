package com.cabin.express.benchmark;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone benchmarking script for CabinServer
 * No external dependencies required - uses only standard Java HTTP client
 */
public class BenchmarkScript {

    private static final String SERVER_URL = "http://localhost:8080"; // Change to your server URL
    private static final int WARMUP_REQUESTS = 100;
    private static final int BENCHMARK_DURATION_SECONDS = 30;
    private static final int[] THREAD_COUNTS = {200}; // Different thread counts to test
    private static final List<BenchmarkResult> allResults = new ArrayList<>();

    // Statistics tracking
    private static class BenchmarkStats {
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong successfulRequests = new AtomicLong(0);
        final AtomicLong totalResponseTime = new AtomicLong(0);
        final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxResponseTime = new AtomicLong(0);
        final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger errors = new AtomicInteger(0);

        void recordRequest(long responseTimeMs, boolean success) {
            totalRequests.incrementAndGet();
            if (success) {
                successfulRequests.incrementAndGet();
                totalResponseTime.addAndGet(responseTimeMs);
                responseTimes.add(responseTimeMs);

                // Update min/max
                minResponseTime.updateAndGet(current -> Math.min(current, responseTimeMs));
                maxResponseTime.updateAndGet(current -> Math.max(current, responseTimeMs));
            } else {
                errors.incrementAndGet();
            }
        }

        double getAverageResponseTime() {
            long successful = successfulRequests.get();
            return successful > 0 ? (double) totalResponseTime.get() / successful : 0;
        }

        double getRequestsPerSecond(long durationSeconds) {
            return (double) totalRequests.get() / durationSeconds;
        }

        long getPercentile(double percentile) {
            if (responseTimes.isEmpty()) return 0;
            List<Long> sorted = new ArrayList<>(responseTimes);
            Collections.sort(sorted);
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, index));
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== CabinServer Benchmark Tool ===");
        System.out.println("Server URL: " + SERVER_URL);
        System.out.println("Benchmark Duration: " + BENCHMARK_DURATION_SECONDS + " seconds per test");
        System.out.println();

        // Check if server is reachable
        if (!isServerReachable()) {
            System.err.println("Server is not reachable at " + SERVER_URL);
            System.err.println("Please start your server and try again.");
            return;
        }

        // Run different benchmark scenarios
        runPostRequestBenchmark();
        runGetRequestBenchmark();
        runMixedWorkloadBenchmark();

        // Add this line
        printSummaryTable();
        System.exit(0);
    }

    private static boolean isServerReachable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER_URL + "/"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runGetRequestBenchmark() throws Exception {
        System.out.println("=== GET Request Benchmark ===");

        for (int threadCount : THREAD_COUNTS) {
            System.out.printf("\nTesting with %d concurrent threads...\n", threadCount);

            BenchmarkStats stats = new BenchmarkStats();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Warmup
            warmup(executor, threadCount);

            // Start benchmark
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> performGetRequests(stats, startTime));
            }

            // Wait for benchmark duration
            Thread.sleep(BENCHMARK_DURATION_SECONDS * 1000);
            executor.shutdownNow();

            printResults("GET", threadCount, stats);
        }
    }

    private static void runPostRequestBenchmark() throws Exception {
        System.out.println("\n=== POST Request Benchmark ===");

        for (int threadCount : THREAD_COUNTS) {
            System.out.printf("\nTesting with %d concurrent threads...\n", threadCount);

            BenchmarkStats stats = new BenchmarkStats();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Warmup
            warmup(executor, threadCount);

            // Start benchmark
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> performPostRequests(stats, startTime));
            }

            // Wait for benchmark duration
            Thread.sleep(BENCHMARK_DURATION_SECONDS * 1000);
            executor.shutdownNow();

            printResults("POST", threadCount, stats);
        }
    }

    private static void runMixedWorkloadBenchmark() throws Exception {
        System.out.println("\n=== Mixed Workload Benchmark (70% GET, 30% POST) ===");

        for (int threadCount : THREAD_COUNTS) {
            System.out.printf("\nTesting with %d concurrent threads...\n", threadCount);

            BenchmarkStats stats = new BenchmarkStats();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Warmup
            warmup(executor, threadCount);

            // Start benchmark
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> performMixedRequests(stats, startTime));
            }

            // Wait for benchmark duration
            Thread.sleep(BENCHMARK_DURATION_SECONDS * 1000);
            executor.shutdownNow();

            printResults("MIXED", threadCount, stats);
        }
    }

    private static void warmup(ExecutorService executor, int threadCount) throws Exception {
        System.out.print("Warming up... ");
        CountDownLatch warmupLatch = new CountDownLatch(WARMUP_REQUESTS);

        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(SERVER_URL + "/"))
                            .GET()
                            .build();

                    client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception ignored) {
                } finally {
                    warmupLatch.countDown();
                }
            });
        }

        warmupLatch.await(30, TimeUnit.SECONDS);
        System.out.println("done");
    }

    private static void performGetRequests(BenchmarkStats stats, long startTime) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Random random = new Random();
        // FIX: Use correct endpoints that actually exist
        String[] endpoints = {
                "/",                           // Root page
                "/health",                     // Health check  
                "/api/test",                   // Test endpoint
                "/api/data",                   // List all data
                "/api/data/key_" + random.nextInt(100), // Get specific key
                "/static/test.txt",            // Static file
                "/static/small.json"           // Small JSON file
        };

        while (System.currentTimeMillis() - startTime < BENCHMARK_DURATION_SECONDS * 100000) {
            long requestStart = System.currentTimeMillis();
            try {
                String endpoint = endpoints[random.nextInt(endpoints.length)];
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SERVER_URL + endpoint))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long requestEnd = System.currentTimeMillis();

                boolean success = response.statusCode() >= 200 && response.statusCode() < 400;
                stats.recordRequest(requestEnd - requestStart, success);

            } catch (Exception e) {
                long requestEnd = System.currentTimeMillis();
                stats.recordRequest(requestEnd - requestStart, false);
            }
        }
    }

    private static void performPostRequests(BenchmarkStats stats, long startTime) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Random random = new Random();
        String[] payloads = {
                "{\"message\": \"hello\"}",
                "{\"data\": \"test data\", \"id\": " + random.nextInt(1000) + "}",
                "{\"user\": \"testuser\", \"action\": \"benchmark\"}"
        };

        while (System.currentTimeMillis() - startTime < BENCHMARK_DURATION_SECONDS * 1000) {
            long requestStart = System.currentTimeMillis();
            try {
                String payload = payloads[random.nextInt(payloads.length)];

                // FIX: Use correct endpoint with key parameter
                String key = "key_" + random.nextInt(1000);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SERVER_URL + "/api/data/" + key))  // ✅ Fixed
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long requestEnd = System.currentTimeMillis();

                boolean success = response.statusCode() >= 200 && response.statusCode() < 400;
                stats.recordRequest(requestEnd - requestStart, success);

            } catch (Exception e) {
                long requestEnd = System.currentTimeMillis();
                stats.recordRequest(requestEnd - requestStart, false);
            }
        }
    }

    private static void performMixedRequests(BenchmarkStats stats, long startTime) {
        Random random = new Random();

        while (System.currentTimeMillis() - startTime < BENCHMARK_DURATION_SECONDS * 1000) {
            if (random.nextInt(100) < 70) {
                // 70% GET requests
                performSingleGetRequest(stats);
            } else {
                // 30% POST requests
                performSinglePostRequest(stats);
            }
        }
    }

    private static final HttpClient sharedClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(50))  // Control max connections
            .build();

    private static void performSingleGetRequest(BenchmarkStats stats) {
        long requestStart = System.currentTimeMillis();
        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER_URL + "/"))
                    .GET()
                    .build();

            HttpResponse<String> response = sharedClient.send(request, HttpResponse.BodyHandlers.ofString());
            long requestEnd = System.currentTimeMillis();

            boolean success = response.statusCode() >= 200 && response.statusCode() < 400;
            stats.recordRequest(requestEnd - requestStart, success);

        } catch (Exception e) {
            long requestEnd = System.currentTimeMillis();
            stats.recordRequest(requestEnd - requestStart, false);
        }
    }

    private static void performSinglePostRequest(BenchmarkStats stats) {
        long requestStart = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER_URL + "/api/data"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"test\": \"data\"}"))
                    .build();

            HttpResponse<String> response = sharedClient.send(request, HttpResponse.BodyHandlers.ofString());
            long requestEnd = System.currentTimeMillis();

            boolean success = response.statusCode() >= 200 && response.statusCode() < 400;
            stats.recordRequest(requestEnd - requestStart, success);
            if (!success) {
                System.err.println("POST request failed with status: " + response.toString());
            }

        } catch (Exception e) {
            long requestEnd = System.currentTimeMillis();
            stats.recordRequest(requestEnd - requestStart, false);
        }
    }

    // Add this class to store benchmark results
    private static class BenchmarkResult {
        final String testType;
        final int threadCount;
        final long totalRequests;
        final long successfulRequests;
        final long failedRequests;
        final double requestsPerSecond;
        final double avgResponseTime;
        final long minResponseTime;
        final long maxResponseTime;
        final long percentile95;
        final long percentile99;
        final double successRate;

        BenchmarkResult(String testType, int threadCount, BenchmarkStats stats) {
            this.testType = testType;
            this.threadCount = threadCount;
            this.totalRequests = stats.totalRequests.get();
            this.successfulRequests = stats.successfulRequests.get();
            this.failedRequests = stats.errors.get();
            this.requestsPerSecond = stats.getRequestsPerSecond(BENCHMARK_DURATION_SECONDS);
            this.avgResponseTime = stats.getAverageResponseTime();
            this.minResponseTime = stats.minResponseTime.get();
            this.maxResponseTime = stats.maxResponseTime.get();
            this.percentile95 = stats.getPercentile(95);
            this.percentile99 = stats.getPercentile(99);
            this.successRate = 100.0 * stats.successfulRequests.get() / stats.totalRequests.get();
        }
    }

    private static void printResultTable(BenchmarkResult result) {
        // Define table format
        String headerFormat = "| %-25s | %-15s |\n";
        String rowFormat = "| %-25s | %-15s |\n";
        String divider = "+---------------------------+-----------------+\n";

        StringBuilder table = new StringBuilder();
        table.append(divider);
        table.append(String.format(headerFormat, "Metric", "Value"));
        table.append(divider);

        // Add rows
        table.append(String.format(rowFormat, "Total Requests", result.totalRequests));
        table.append(String.format(rowFormat, "Successful Requests", result.successfulRequests));
        table.append(String.format(rowFormat, "Failed Requests", result.failedRequests));
        table.append(String.format(rowFormat, "Requests/Second", String.format("%.2f", result.requestsPerSecond)));
        table.append(String.format(rowFormat, "Avg Response Time (ms)", String.format("%.2f", result.avgResponseTime)));
        table.append(String.format(rowFormat, "Min Response Time (ms)", result.minResponseTime));
        table.append(String.format(rowFormat, "Max Response Time (ms)", result.maxResponseTime));
        table.append(String.format(rowFormat, "95th Percentile (ms)", result.percentile95));
        table.append(String.format(rowFormat, "99th Percentile (ms)", result.percentile99));
        table.append(String.format(rowFormat, "Success Rate", String.format("%.2f%%", result.successRate)));
        table.append(divider);

        System.out.print(table.toString());
    }


    // Update the printResults method to ensure results are stored
    private static void printResults(String testType, int threadCount, BenchmarkStats stats) {
        // Create and store the result
        BenchmarkResult result = new BenchmarkResult(testType, threadCount, stats);
        allResults.add(result);  // Make sure this line executes

        // Print individual result as table
        System.out.printf("\n--- %s Results (%d threads) ---\n", testType, threadCount);
        printResultTable(result);
    }

    // Add this method at the end of main() to print summary table
    private static void printSummaryTable() {
        System.out.println("\n=== BENCHMARK SUMMARY ===");

        // Define table format
        String headerFormat = "| %-10s | %-7s | %-8s | %-9s | %-8s | %-8s | %-8s | %-8s |\n";
        String rowFormat = "| %-10s | %-7d | %-8d | %-9.2f | %-8.2f | %-8d | %-8d | %-8.2f |\n";
        String divider = "+------------+---------+----------+-----------+----------+----------+----------+----------+\n";

        StringBuilder table = new StringBuilder();
        table.append(divider);
        table.append(String.format(headerFormat, "Test Type", "Threads", "Requests", "Req/Sec", "Avg (ms)", "95th (ms)", "99th (ms)", "Success%"));
        table.append(divider);

        // Add rows for each result
        for (BenchmarkResult result : allResults) {
            table.append(String.format(rowFormat,
                    result.testType,
                    result.threadCount,
                    result.totalRequests,
                    result.requestsPerSecond,
                    result.avgResponseTime,
                    result.percentile95,
                    result.percentile99,
                    result.successRate));
        }
        table.append(divider);

        System.out.print(table.toString());
    }
}