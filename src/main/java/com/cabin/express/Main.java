package com.cabin.express;

import com.cabin.express.interfaces.Middleware;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;

public class Main {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinLogger.setDebug(enableDebug);

        CabinLogger.info("Starting CabinJ Framework...");
        try {
            CabinServer server = new CabinServer();
            Router appRouter = new Router();
            Router apiRouter = new Router();

            Middleware authMiddleware = (request, response, next) -> {
                CabinLogger.debug("Authenticating user...");
                System.err.println("Body: " + request.getBody());

                if(request.getBody().equals("Sang")) {
                    next.next(request, response);
                } else {
                    response.writeBody("Unauthorized");
                    response.setStatusCode(401);
                    response.send();
                }

            };

            apiRouter.use(authMiddleware);


            appRouter.get("/", (req, res) -> {
                res.writeBody("Hello, world!");
                res.send();
            });

            apiRouter.post("/login", (req, res) -> {
                res.writeBody("Login successful");
                res.send();
            });

            server.use(appRouter);
            server.use(apiRouter);

            server.listen(8080);

        } catch (Exception e) {
            CabinLogger.error("Failed to start the server", e);
        }
    }
}