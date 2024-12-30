package com.cabin.express.zdemo;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

import java.util.Map;

public class CabinDemoServer  {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinLogger.setDebug(enableDebug);

        CabinLogger.info("Starting CabinJ Framework...");
        try {
            CabinServer server = new ServerBuilder().build();

            Middleware authMiddleware = (req, res, next) -> {
                Map<String, Object> bodyAsJson = req.getBody();
                if (bodyAsJson.getOrDefault("username", "").equals("Sang")) {
                    next.next(req, res);
                } else {
                    res.writeBody("Unauthorized");
                    res.setStatusCode(401);
                    res.send();
                }
            };

            server.use(ApiRouter.INSTANCE.getRouter());
            server.use(AppRouter.INSTANCE.getRouter());

            server.start();

        } catch (Exception e) {
            CabinLogger.error("Failed to start the server", e);
        }
    }
}