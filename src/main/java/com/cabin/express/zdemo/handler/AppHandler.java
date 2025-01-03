package com.cabin.express.zdemo.handler;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;

import java.io.IOException;
import java.util.Map;

public class AppHandler {
    public static final AppHandler Instance = new AppHandler();

    private AppHandler() {
    }

    public void hello(Request req, Response resp) throws IOException {
        String appId = req.getPathParam("appId");
        String userId = req.getPathParam("userId");
        System.err.println("User ID: " + userId);
        System.err.println("App ID: " + appId);
        resp.writeBody("Hello, User ID: " + userId + ", App ID: " + appId);
        resp.send();
    }

    public void updateUserInfo(Request req, Response resp) throws IOException {
        Long userId = req.getPathParamAsLong("userId", 0L);
        System.err.println("User ID: " + userId);
        Map<String, Object> body = req.getBody();

        System.err.println("Request body: " + body);
        resp.writeBody("User ID: " + userId);

        resp.send();
    }
}
