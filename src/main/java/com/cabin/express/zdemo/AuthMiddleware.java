package com.cabin.express.zdemo;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.middleware.MiddlewareChain;

import java.io.IOException;

public class AuthMiddleware {
    public static final AuthMiddleware Instance = new AuthMiddleware();

    private AuthMiddleware() {
    }

    // Middleware to check if the user is authenticated
    public void checkAuth(Request req, Response res, MiddlewareChain next) throws IOException {
        System.err.println("Checking auth..., time: " + System.currentTimeMillis());
        String token = req.getHeader("Authorization");
        if (token == null || !token.equals("Bearer token")) {
            res.setStatusCode(401);
            res.writeBody("Unauthorized");
            res.send();
        }
        next.next(req, res);
    }
}
