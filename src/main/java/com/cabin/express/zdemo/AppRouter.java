package com.cabin.express.zdemo;

import com.cabin.express.router.Router;
import com.cabin.express.zdemo.handler.AppHandler;

public class AppRouter {

    public static final AppRouter Instance = new AppRouter();
    public static final String API_PREFIX = "/api";

    public Router registerRoutes() {
        Router router = new Router();

        router.get("/hello", AppHandler.Instance::hello);
        router.get("/users", AppHandler.Instance::getUserInfo);

        return router;
    }
}
