package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.logger.CabinLogger;


import java.io.IOException;

public class GzipMiddleware implements Middleware {
    @Override
    public void apply(Request req, Response res, MiddlewareChain chain) throws IOException {
        String acceptEncoding = req.getHeader("Accept-Encoding");
        if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
            try {
                res.enableCompression();
            } catch (Exception e) {
                CabinLogger.error("Error enabling GZIP compression: " + e.getMessage(), e);
                // Continue without compression as fallback
            }
        }
        // Proceed down the chain regardless
        chain.next(req, res);
    }
}