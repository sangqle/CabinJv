package com.cabin.express.zdemo;

import com.cabin.express.router.Router;
import com.cabin.express.zdemo.handler.AppHandler;

public class AppRouter {

    public static final AppRouter Instance = new AppRouter();
    public static final String API_PREFIX = "/api/";

    public Router registerRoutes() {
        Router router = new Router();

        router.setPrefix(API_PREFIX);

        router.get("/hello", AppHandler.Instance::hello);
        router.post("/users", AppHandler.Instance::addUser);
        router.get("/users", AppHandler.Instance::getSliceUsers);

        router.post("/products", AppHandler.Instance::addProduct);
        router.get("/products", AppHandler.Instance::getSliceProducts);

        System.err.println("Endpoints registered: " + router.getEndpoint());

        return router;
    }
}
