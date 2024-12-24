package com.cabin.express;

import com.cabin.express.router.CabinRouter;
import com.cabin.express.server.CabinJServer;

public class Main {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinJLogger.setDebug(enableDebug);

        CabinJLogger.info("Starting CabinJ Framework...");
        try {
            CabinJServer server = new CabinJServer();
            CabinRouter router = getCabinRouter();

            server.addRoute(router);

            server.listen(8080);

        } catch (Exception e) {
            CabinJLogger.error("Failed to start the server", e);
        }
    }

    private static CabinRouter getCabinRouter() {
        CabinRouter router = new CabinRouter();
        // Register GET /hello
        router.addRoute("GET", "/hello", (request, response) -> {
            response.setStatusCode(200);
            response.writeBody("Hello, World!");
        });

        router.addRoute("POST", "/data", (request, response) -> {
            String body = request.getBody();
            response.setStatusCode(200);
            response.writeBody("Data received: " + body);
        });
        return router;
    }
}