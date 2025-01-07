package com.cabin.express.zdemo.handler;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.zdemo.db.UserMySQL;
import com.cabin.express.zdemo.dto.UserInfo;

import java.util.HashMap;
import java.util.List;

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

    public void addUser(Request req, Response resp) throws IOException {
        Long userId = req.getPathParamAsLong("userId", 0L);

        UserInfo bodyAs = req.getBodyAs(UserInfo.class);
        System.err.println("Request body: " + bodyAs);

        // Add User into db
        int i = UserMySQL.Instance.addUser(bodyAs);

        resp.writeBody("User added: " + i);

        resp.send();
    }

    public void getSliceUsers(Request req, Response resp) throws IOException {
        int offset = req.getQueryParamAsInt("offset", 0);
        int limit = req.getQueryParamAsInt("limit", 10);

        List<UserInfo> users = UserMySQL.Instance.getSlice(offset, limit);

        Map<String, Object> data = new HashMap<>();
        data.put("users", users);
        resp.writeJsonBody(data);

        resp.send();
    }
}
