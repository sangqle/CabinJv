package com.cabin.express;

import com.cabin.express.interfaces.Middleware;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinLogger.setDebug(enableDebug);

        CabinLogger.info("Starting CabinJ Framework...");
        try {
            CabinServer server = new ServerBuilder().build();

            Router appRouter = new Router();
            Router apiRouter = new Router();

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

            appRouter.get("/", (req, res) -> {
                res.writeBody("Hello, world!");
                res.send();
            });

            apiRouter.post("/private", (req, res) -> {
                res.writeBody("This is private data");
                res.send();
            });

            apiRouter.use(authMiddleware);

            server.use(appRouter);
            server.use(apiRouter);

            server.start();

        } catch (Exception e) {
            CabinLogger.error("Failed to start the server", e);
        }
    }
}