package com.cabin.express.exception;

import com.cabin.express.http.Response;
import com.cabin.express.loggger.CabinLogger;

public class GlobalExceptionHandler {
    public static void handleException(Throwable e, Response response) {
        if (e instanceof CabinException) {
            response.setStatusCode(400);
            response.writeBody(e.getMessage());
        } else {
            response.setStatusCode(500);
            response.writeBody("Internal Server Error");
        }
        response.send();
        CabinLogger.error("Exception handled: " + e.getMessage(), e);
    }
}