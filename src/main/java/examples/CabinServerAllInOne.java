package examples;

import com.cabin.express.middleware.GzipMiddleware;
import com.cabin.express.middleware.StaticMiddleware;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CabinServerAllInOne {
    private static final Logger logger = LoggerFactory.getLogger(CabinServerAllInOne.class);
    private static CabinServer server;

    public static void main(String[] args) throws IOException {
        server = new ServerBuilder()
                .setPort(8888)
                .enableProfiler(true)
                .enableProfilerDashboard(true)
                .build();

        Router router = new Router();

        router.get("/hello", (req, res) -> {
            throw new RuntimeException("This is a test exception to demonstrate error handling.");
        });


        router.get("/", (req, res) -> {
            JsonObject json = new JsonObject();
            json.addProperty("message", "Hello, World!");
            res.send(json);
        });

        router.get("/large", (req, res) -> {
            StringBuilder largeResponse = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                largeResponse.append("Hello, World! ");
            }
            res.send(largeResponse.toString());
        });

        // Route to stop the server gracefully
        router.get("/stop", (req, res) -> {
            res.writeBody("Stopping server...");
            res.send();

            // Stop the server in a separate thread after sending the response
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Wait a second before stopping
                    boolean stopped = server.stop(10000); // Stop with 10 second timeout
                    logger.info("Server stopped successfully: " + stopped);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Error while stopping server", e);
                }
            }).start();
        });

        // Static file serving
        server.use("/api", router);
        server.use(new GzipMiddleware());

        // Create static middleware and exclude API paths using the new pattern
        StaticMiddleware staticMiddleware = new StaticMiddleware("public", "/")
                .excludePrefixes("/api", "/graph/");

        // Add static middleware last
        server.use(staticMiddleware);

        // Start the server in the main thread
        logger.info("Starting server at http://localhost:" + server.getPort());
        server.start();
    }
}
