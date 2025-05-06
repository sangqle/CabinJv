package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class GzipMiddleware implements Middleware {
    private static final int MIN_SIZE = 512; // optional threshold

    @Override
    public void apply(Request req, Response res, MiddlewareChain next) throws IOException {
        String accept = req.getHeaders().getOrDefault("Accept-Encoding", "");
        if (!accept.contains("gzip")) {
            next.next(req, res);
            return;
        }

        res.getHeaders().put("Content-Encoding", "gzip");
        OutputStream originalOut = res.getOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(originalOut, true);

        res.setOutputStream(gzipOut);
        try {
            next.next(req, res);
        } finally {
            try {
                gzipOut.close();    // calls finish() and then deflater.end()
            } catch (IOException e) {
                // client may have closed; swallow broken-pipe here
            }
            res.setOutputStream(originalOut);
        }
    }
}
