package com.cabin.express.zdemo;

import com.cabin.express.router.Router;

import java.util.Set;

public class AppRouter {

    private Router router = new Router();

    public static final AppRouter INSTANCE = new AppRouter();

    private AppRouter() {
        init();
    }

    public Router getRouter() {
        return router;
    }

    public void init() {
        router.get("/hello", (req, res) -> {
            res.writeBody("Hello, world!");
            res.send();
        });

        router.post("/private-v2", (req, res) -> {
            res.writeBody("This is private data...");
            res.send();
        });
    }

}
