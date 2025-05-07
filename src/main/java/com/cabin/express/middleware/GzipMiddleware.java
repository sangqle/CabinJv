package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.stream.SimpleChunkedOutputStream;


import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class GzipMiddleware implements Middleware {
    @Override
    public void apply(Request req, Response res, MiddlewareChain next) throws IOException {
        String accept = req.getHeaders().getOrDefault("Accept-Encoding", "");
        if (!accept.contains("gzip")) {
            next.next(req, res);
            return;
        }

        // Tell client we’ll gzip + chunk the BODY
        res.getHeaders().put("Content-Encoding", "gzip");
        res.getHeaders().put("Transfer-Encoding", "chunked");
        res.getHeaders().put("Vary", "Accept-Encoding");
        res.getHeaders().remove("Content-Length");

        // Wrap only the body stream
        SimpleChunkedOutputStream chunked =
                new SimpleChunkedOutputStream(res.getOutputStream());
        GZIPOutputStream gzip =
                new GZIPOutputStream(chunked, true);
        res.setOutputStream(gzip);

        try {
            next.next(req, res);
        } finally {
            // clean up, swallowing broken-pipe
            try { gzip.finish(); } catch(IOException ignore){}
            try { gzip.close();  } catch(IOException ignore){}
            try { chunked.finish(); } catch(IOException ignore){}
        }
    }
}

