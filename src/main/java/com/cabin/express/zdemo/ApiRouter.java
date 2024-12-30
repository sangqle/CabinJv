package com.cabin.express.zdemo;

import com.cabin.express.router.Router;

public class ApiRouter {
    private Router router = new Router();

    public static final ApiRouter INSTANCE = new ApiRouter();

    private ApiRouter() {
        init();
    }

    public Router getRouter() {
        return router;
    }

    public void init() {
        router.get("/", (req, res) -> {
            res.writeBody("this is api router !");
            res.send();
        });

        router.post("/private", (req, res) -> {
            res.writeBody("this is private data... of api router");
            res.send();
        });
    }
}
