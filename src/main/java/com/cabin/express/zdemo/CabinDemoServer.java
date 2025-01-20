package com.cabin.express.zdemo;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

import java.io.IOException;

public class CabinDemoServer {
    public static void main(String[] args) throws IOException {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinLogger.setDebug(enableDebug);

        CabinLogger.info("Starting CabinJ Framework...");
        try {
            CabinServer server = new ServerBuilder().setMaxPoolSize(200).setMaxQueueCapacity(1000).build();
            server.use(AppRouter.Instance.registerRoutes());
            server.use(ApiRouter.Instance.registerRoutes());

            server.start();

        } catch (Exception e) {
            CabinLogger.error("Failed to start the server", e);
        }
    }
}