package com.cabin.express.zdemo.handler;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.zdemo.dto.UserInfo;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;

public class AppHandler {
    public static final AppHandler Instance = new AppHandler();

    private AppHandler() {
    }

    public void hello(Request req, Response resp) throws IOException {
        try {
            int appId = req.getQueryParamAsInt("appId", 0);
            long userId = req.getQueryParamAsLong("userId", 0L);
            Thread.sleep(Math.round(Math.random() * 500));
            resp.writeBody("Hello, User ID: " + userId + ", App ID: " + appId);
        } catch (Exception e) {
            CabinLogger.error(e.getMessage(), e);
            resp.writeBody("Hello, User ID: 0, App ID: 0");
        }
        resp.send();
    }

    public void updateUserInfo(Request req, Response resp) throws IOException {
        Long userId = req.getPathParamAsLong("userId", 0L);
        System.err.println("User ID: " + userId);

        UserInfo bodyAs = req.getBodyAs(UserInfo.class);


        resp.clearCookie("userId", "localhost", "/");

        System.err.println("Request body: " + bodyAs);
        resp.writeJsonBody(bodyAs);

        resp.send();
    }
}
