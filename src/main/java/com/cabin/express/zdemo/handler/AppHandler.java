package com.cabin.express.zdemo.handler;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;

import java.io.IOException;

public class AppHandler {
    public static final AppHandler Instance = new AppHandler();

    private AppHandler() {
    }

    public void hello(Request req, Response resp) throws IOException {
        resp.writeBody("Hello, World!");
        resp.send();
    }

    public void getUserInfo(Request req, Response resp) throws IOException {
        resp.writeBody("User Info: {\"name\": \"John Doe\", \"age\": 30}");
        resp.send();
    }
}
