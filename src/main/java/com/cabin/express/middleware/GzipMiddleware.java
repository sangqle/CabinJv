package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;


import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class GzipMiddleware implements Middleware {
    @Override
    public void apply(Request req, Response res, MiddlewareChain chain) throws IOException {
        String acceptEncoding = req.getHeader("Accept-Encoding");

        if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
            try {
                // Signal to response that compression is needed
                res.enableCompression();

                // Create and set the GZIP output stream
                GZIPOutputStream gzipOut = new GZIPOutputStream(res.getOutputStream());
                res.setOutputStream(gzipOut);

                // Set the appropriate header
                res.addHeader("Content-Encoding", "gzip");

                // Process the chain
                chain.next(req, res);

                // Finalize GZIP data
                gzipOut.finish();
                gzipOut.flush();
            } catch (IOException e) {
                CabinLogger.error("Error setting up GZIP compression: " + e.getMessage(), e);
                // Continue without compression as fallback
                chain.next(req, res);
            }
        } else {
            // No compression requested, just process the chain
            chain.next(req, res);
        }
    }
}
