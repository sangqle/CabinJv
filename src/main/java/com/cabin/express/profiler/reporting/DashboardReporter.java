package com.cabin.express.profiler.reporting;

import com.cabin.express.profiler.ServerProfiler;
import com.cabin.express.profiler.metrics.ThreadMetrics;
import com.cabin.express.router.Router;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * DashboardReport provides a web-based dashboard for viewing profiler metrics.
 * It sets up routes for the dashboard UI and metrics endpoints.
 */
public class DashboardReporter implements MetricsReporter {
    private final Gson gson = new Gson();
    private Router adminRouter;

    public DashboardReporter() {
        // Initialize router with admin prefix
        this.adminRouter = new Router();
        setupRoutes();
    }

    /**
     * Set up the dashboard routes
     */
    private void setupRoutes() {
        // Metrics API endpoint
        adminRouter.get("/metrics", (req, res) -> {
            res.setHeader("Content-Type", "application/json");

            // Get current metrics snapshot
            ServerProfiler.ProfilerSnapshot snapshot = ServerProfiler.INSTANCE.getSnapshot();

            // Convert to JSON response
            Map<String, Object> response = buildMetricsResponse(snapshot);

            res.send(gson.toJson(response));
        });

        // Dashboard HTML page
        adminRouter.get("/dashboard", (req, res) -> {
            res.setHeader("Content-Type", "text/html");
            res.send(loadDashboardHtml());
        });
    }

    /**
     * Get the admin router that serves the dashboard UI and metrics
     * @return the router with dashboard routes
     */
    public Router getRouter() {
        return adminRouter;
    }

    /**
     * Build the metrics response object from a snapshot
     */
    private Map<String, Object> buildMetricsResponse(ServerProfiler.ProfilerSnapshot snapshot) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", snapshot.getTimestamp());

        // Memory metrics
        Map<String, Object> memory = new HashMap<>();
        memory.put("heapUsedMB", snapshot.getMemoryMetrics().getHeapUsed() / (1024 * 1024));
        memory.put("heapMaxMB", snapshot.getMemoryMetrics().getHeapMax() / (1024 * 1024));
        memory.put("heapUtilization", snapshot.getMemoryMetrics().getHeapUtilization());
        memory.put("nonHeapUsedMB", snapshot.getMemoryMetrics().getNonHeapUsed() / (1024 * 1024));

        // CPU metrics
        Map<String, Object> cpu = new HashMap<>();
        cpu.put("processCpuPercent", snapshot.getCpuMetrics().getProcessCpuLoad() * 100);
        cpu.put("systemCpuPercent", snapshot.getCpuMetrics().getSystemCpuLoad() * 100);
        cpu.put("processors", snapshot.getCpuMetrics().getAvailableProcessors());

        // Thread metrics
        Map<String, Object> threads = new HashMap<>();
        threads.put("threadCount", snapshot.getThreadMetrics().getThreadCount());
        threads.put("peakThreadCount", snapshot.getThreadMetrics().getPeakThreadCount());

        // Include worker pools
        Map<String, Object> pools = new HashMap<>();
        for (Map.Entry<String, ThreadMetrics.WorkerPoolMetrics> entry :
                snapshot.getThreadMetrics().getWorkerPoolMetrics().entrySet()) {
            Map<String, Object> poolMetrics = new HashMap<>();
            poolMetrics.put("size", entry.getValue().getPoolSize());
            poolMetrics.put("active", entry.getValue().getActiveThreads());
            poolMetrics.put("queued", entry.getValue().getQueueSize());
            poolMetrics.put("completed", entry.getValue().getCompletedTasks());
            pools.put(entry.getKey(), poolMetrics);
        }
        threads.put("workerPools", pools);

        // Request metrics
        Map<String, Object> requests = new HashMap<>();
        requests.put("totalRequests", snapshot.getRequestMetrics().getTotalRequests());
        requests.put("successfulRequests", snapshot.getRequestMetrics().getSuccessfulRequests());
        requests.put("failedRequests", snapshot.getRequestMetrics().getFailedRequests());
        requests.put("avgResponseTimeMs", snapshot.getRequestMetrics().getAverageResponseTimeMs());

        // Top paths
        requests.put("topPaths", snapshot.getRequestMetrics().getTopPaths(5));

        // Add to main response
        result.put("memory", memory);
        result.put("cpu", cpu);
        result.put("threads", threads);
        result.put("requests", requests);

        return result;
    }

    /**
     * Load the dashboard HTML content
     */
    private String loadDashboardHtml() {
        // This would typically load from a resource file
        // For this example, we'll return a simple HTML snippet
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>CabinServer Profiler Dashboard</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f7fa; }\n" +
                "        .dashboard { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; }\n" +
                "        .card { background: white; border: 1px solid #ddd; border-radius: 4px; padding: 15px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }\n" +
                "        .card h3 { margin-top: 0; border-bottom: 1px solid #eee; padding-bottom: 10px; color: #333; }\n" +
                "        table { width: 100%; border-collapse: collapse; margin-bottom: 15px; }\n" +
                "        th { text-align: left; background-color: #f7f9fc; }\n" +
                "        td, th { padding: 8px; border-bottom: 1px solid #eee; }\n" +
                "        .value { font-weight: bold; text-align: right; }\n" +
                "        .header-row { background-color: #f0f2f5; }\n" +
                "        .path-table { font-size: 0.9em; }\n" +
                "        .last-updated { text-align: right; font-size: 0.8em; color: #888; margin-top: 10px; }\n" +
                "        .metric-name { width: 60%; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>CabinServer Profiler Dashboard</h1>\n" +
                "    <div id=\"dashboard\">\n" +
                "        <p>Loading metrics data...</p>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        function fetchMetrics() {\n" +
                "            fetch('/admin/metrics')\n" +
                "                .then(response => response.json())\n" +
                "                .then(data => updateDashboard(data))\n" +
                "                .catch(error => console.error('Error fetching metrics:', error));\n" +
                "        }\n" +
                "        \n" +
                "        function updateDashboard(data) {\n" +
                "            const dashboard = document.getElementById('dashboard');\n" +
                "            \n" +
                "            // Format timestamp\n" +
                "            const timestamp = new Date(data.timestamp).toLocaleString();\n" +
                "            \n" +
                "            // Create main layout\n" +
                "            let html = `<div class=\"dashboard\">`;\n" +
                "            \n" +
                "            // Memory card\n" +
                "            html += `\n" +
                "                <div class=\"card\">\n" +
                "                    <h3>Memory</h3>\n" +
                "                    <table>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Heap Used</td>\n" +
                "                            <td class=\"value\">${data.memory.heapUsedMB} MB</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Heap Max</td>\n" +
                "                            <td class=\"value\">${data.memory.heapMaxMB} MB</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Heap Utilization</td>\n" +
                "                            <td class=\"value\">${(data.memory.heapUtilization * 100).toFixed(2)}%</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Non-Heap Used</td>\n" +
                "                            <td class=\"value\">${data.memory.nonHeapUsedMB} MB</td>\n" +
                "                        </tr>\n" +
                "                    </table>\n" +
                "                </div>`;\n" +
                "            \n" +
                "            // CPU card\n" +
                "            html += `\n" +
                "                <div class=\"card\">\n" +
                "                    <h3>CPU</h3>\n" +
                "                    <table>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Process CPU</td>\n" +
                "                            <td class=\"value\">${data.cpu.processCpuPercent > 0 ? data.cpu.processCpuPercent.toFixed(2) + '%' : 'N/A'}</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">System CPU</td>\n" +
                "                            <td class=\"value\">${data.cpu.systemCpuPercent > 0 ? data.cpu.systemCpuPercent.toFixed(2) + '%' : 'N/A'}</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Processors</td>\n" +
                "                            <td class=\"value\">${data.cpu.processors}</td>\n" +
                "                        </tr>\n" +
                "                    </table>\n" +
                "                </div>`;\n" +
                "            \n" +
                "            // Threads card\n" +
                "            html += `\n" +
                "                <div class=\"card\">\n" +
                "                    <h3>Threads</h3>\n" +
                "                    <table>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Thread Count</td>\n" +
                "                            <td class=\"value\">${data.threads.threadCount}</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Peak Thread Count</td>\n" +
                "                            <td class=\"value\">${data.threads.peakThreadCount}</td>\n" +
                "                        </tr>\n" +
                "                    </table>`;\n" +
                "            \n" +
                "            // Worker pools table (if any)\n" +
                "            const pools = data.threads.workerPools;\n" +
                "            if (Object.keys(pools).length > 0) {\n" +
                "                html += `\n" +
                "                    <h4>Worker Pools</h4>\n" +
                "                    <table>\n" +
                "                        <tr class=\"header-row\">\n" +
                "                            <th>Name</th>\n" +
                "                            <th>Size</th>\n" +
                "                            <th>Active</th>\n" +
                "                            <th>Queued</th>\n" +
                "                        </tr>`;\n" +
                "                \n" +
                "                for (const [name, pool] of Object.entries(pools)) {\n" +
                "                    html += `\n" +
                "                        <tr>\n" +
                "                            <td>${name}</td>\n" +
                "                            <td class=\"value\">${pool.size}</td>\n" +
                "                            <td class=\"value\">${pool.active}</td>\n" +
                "                            <td class=\"value\">${pool.queued}</td>\n" +
                "                        </tr>`;\n" +
                "                }\n" +
                "                html += `</table>`;\n" +
                "            }\n" +
                "            \n" +
                "            html += `</div>`; // Close threads card\n" +
                "            \n" +
                "            // Requests card\n" +
                "            html += `\n" +
                "                <div class=\"card\">\n" +
                "                    <h3>Requests</h3>\n" +
                "                    <table>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Total Requests</td>\n" +
                "                            <td class=\"value\">${data.requests.totalRequests}</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Successful Requests</td>\n" +
                "                            <td class=\"value\">${data.requests.successfulRequests}</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Failed Requests</td>\n" +
                "                            <td class=\"value\">${data.requests.failedRequests}</td>\n" +
                "                        </tr>\n" +
                "                        <tr>\n" +
                "                            <td class=\"metric-name\">Avg Response Time</td>\n" +
                "                            <td class=\"value\">${data.requests.avgResponseTimeMs.toFixed(2)} ms</td>\n" +
                "                        </tr>\n" +
                "                    </table>\n" +
                "                    \n" +
                "                    <h4>Top Paths</h4>\n" +
                "                    <table class=\"path-table\">\n" +
                "                        <tr class=\"header-row\">\n" +
                "                            <th>Path</th>\n" +
                "                            <th>Count</th>\n" +
                "                            <th>Avg Time (ms)</th>\n" +
                "                            <th>Errors</th>\n" +
                "                        </tr>`;\n" +
                "            \n" +
                "            // Add top paths\n" +
                "            data.requests.topPaths.forEach(path => {\n" +
                "                html += `\n" +
                "                    <tr>\n" +
                "                        <td>${path.path}</td>\n" +
                "                        <td class=\"value\">${path.count}</td>\n" +
                "                        <td class=\"value\">${path.avgResponseTimeMs.toFixed(2)}</td>\n" +
                "                        <td class=\"value\">${path.errorCount || 0}</td>\n" +
                "                    </tr>`;\n" +
                "            });\n" +
                "            \n" +
                "            html += `\n" +
                "                    </table>\n" +
                "                </div>\n" +
                "            </div>`; // Close requests card and dashboard grid\n" +
                "            \n" +
                "            // Add last updated time\n" +
                "            html += `<div class=\"last-updated\">Last updated: ${timestamp}</div>`;\n" +
                "            \n" +
                "            // Set the HTML\n" +
                "            dashboard.innerHTML = html;\n" +
                "        }\n" +
                "        \n" +
                "        // Update every 5 seconds\n" +
                "        setInterval(fetchMetrics, 5000);\n" +
                "        fetchMetrics(); // Initial fetch\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    @Override
    public void report(ServerProfiler.ProfilerSnapshot snapshot) {
        // This reporter doesn't need to actively push metrics
        // since it provides them on-demand via HTTP requests
    }
}