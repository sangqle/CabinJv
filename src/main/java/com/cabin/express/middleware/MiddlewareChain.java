package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;

import java.io.IOException;
import java.util.List;
import java.util.Iterator;

public class MiddlewareChain {
    private final Iterator<Middleware> middlewareIterator;
    private final Handler routeHandler;

    public MiddlewareChain(List<Middleware> middleware, Handler routeHandler) {
        this.middlewareIterator = middleware.iterator();
        this.routeHandler = routeHandler;
    }

    public void next(Request request, Response response) throws IOException {
        if (middlewareIterator.hasNext()) {
            Middleware current = middlewareIterator.next();
            current.apply(request, response, this);
        } else if (routeHandler != null) {
            routeHandler.handle(request, response);
        }
    }
}
