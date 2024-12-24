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
            CabinRouter router = new CabinRouter();

            router.get("/", (req, res) -> {
                res.writeBody("Hello, world!");
                res.send();
            });

            server.use(router);

            server.listen(8080);

        } catch (Exception e) {
            CabinJLogger.error("Failed to start the server", e);
        }
    }
}