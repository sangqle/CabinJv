package com.cabin.express.zdemo;

import com.cabin.express.router.Router;

public class ApiRouter {

    public static final ApiRouter  Instance = new ApiRouter();

    public Router registerRoutes() {
        Router router = new Router();
        router.get("/", (req, res) -> {
            res.writeBody("this is api router !");
            res.send();
        });

        router.post("/private", (req, res) -> {
            res.writeBody("this is private data... of api router");
            res.send();
        });
        return router;
    }
}
