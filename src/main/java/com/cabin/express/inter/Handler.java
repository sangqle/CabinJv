package com.cabin.express.inter;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;

import java.io.IOException;

@FunctionalInterface
public interface Handler {
    void handle(Request request, Response response) throws IOException;
}