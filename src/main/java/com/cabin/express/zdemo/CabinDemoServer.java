package com.cabin.express.zdemo;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.middleware.CORS;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

import java.util.Arrays;
import java.util.List;

public class CabinDemoServer  {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinLogger.setDebug(enableDebug);

        CabinLogger.info("Starting CabinJ Framework...");
        try {
            CabinServer server = new ServerBuilder().setMaxPoolSize(200).setMaxQueueCapacity(100).build();

            List<String> allowedOrigins = Arrays.asList("https://viblo.asia");
            List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS");
            List<String> allowedHeaders = Arrays.asList("Content-Type", "Authorization");

            CORS corsMiddleware = new CORS(allowedOrigins, allowedMethods, allowedHeaders, true);


            server.use(corsMiddleware);
            server.use(AppRouter.Instance.registerRoutes());
            server.use(ApiRouter.Instance.registerRoutes());

            server.start();

        } catch (Exception e) {
            CabinLogger.error("Failed to start the server", e);
        }
    }
}