package com.cabin.express.zdemo;

import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.router.Router;

public class ApiRouter {

    public static final ApiRouter  Instance = new ApiRouter();

    public Router registerRoutes() {
        Router router = new Router();

        router.get("/", (req, res) -> {
            res.writeBody("this is api router !");
            CabinLogger.info("GET /");
            res.send();
        });

        return router;
    }
}
