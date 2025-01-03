package com.cabin.express.zdemo;

import com.cabin.express.interfaces.Middleware;
import com.cabin.express.router.Router;
import com.cabin.express.zdemo.handler.AppHandler;

public class AppRouter {

    public static final AppRouter Instance = new AppRouter();
    public static final String API_PREFIX = "/api/v1";

    public Router registerRoutes() {
        Router router = new Router();

        router.setPrefix(API_PREFIX);

        router.get("/hello", AppHandler.Instance::hello);
        router.get("/users", AppHandler.Instance::getUserInfo);


        System.err.println("Endpoints registered: " + router.getEndpoint());

        return router;
    }
}
