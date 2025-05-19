package com.cabin.express.debug;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.middleware.MiddlewareChain;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ConcurrentModificationException;

public class ThreadSafetyDebugMiddleware implements Middleware {
    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        try {
            next.next(request, response);
        } catch (ConcurrentModificationException e) {
            // Special handling for thread safety issues
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            
            String requestInfo = "";
            try {
                requestInfo = "Path: " + request.getPath() + 
                             ", Method: " + request.getMethod() +
                             ", Thread: " + Thread.currentThread().getName();
            } catch (Exception ex) {
                requestInfo = "Could not get request info: " + ex.getMessage();
            }
            
            CabinLogger.error("Thread safety issue detected!\n" +
                             "Request: " + requestInfo + "\n" +
                             "Exception: " + e.getClass().getName() + " - " + e.getMessage() + "\n" +
                             "Stack trace:\n" + sw.toString(), e);
            
            // Create a new response to avoid using the potentially corrupted one
            response.setStatusCode(500);
            response.writeBody("Thread safety error detected: " + e.getClass().getSimpleName());
            response.send();
        } catch (Exception e) {
            // Handle other exceptions
            throw e;
        }
    }
}