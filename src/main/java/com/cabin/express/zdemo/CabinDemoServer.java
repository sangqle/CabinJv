package com.cabin.express.zdemo;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

public class CabinDemoServer  {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinLogger.setDebug(enableDebug);

        CabinLogger.info("Starting CabinJ Framework...");
        try {
            CabinServer server = new ServerBuilder().build();



            server.use(AuthMiddleware.Instance::checkAuth);

            server.use(AppRouter.Instance.registerRoutes());
            server.use(ApiRouter.Instance.registerRoutes());

            server.start();

        } catch (Exception e) {
            CabinLogger.error("Failed to start the server", e);
        }
    }
}