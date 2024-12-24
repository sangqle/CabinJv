package com.cabin.express;

import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;

public class Main {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinLogger.setDebug(enableDebug);

        CabinLogger.info("Starting CabinJ Framework...");
        try {
            CabinServer server = new CabinServer();
            Router router = new Router();

            router.get("/", (req, res) -> {
                res.writeBody("Hello, world!");
                res.send();
            });

            server.use(router);

            server.listen(8080);

        } catch (Exception e) {
            CabinLogger.error("Failed to start the server", e);
        }
    }
}