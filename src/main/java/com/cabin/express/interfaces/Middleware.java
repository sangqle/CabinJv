package com.cabin.express.interfaces;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.middleware.MiddlewareChain;

import java.io.IOException;

@FunctionalInterface
public interface Middleware {
    void apply(Request request, Response response, MiddlewareChain next) throws IOException;
}
