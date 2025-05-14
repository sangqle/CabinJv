package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.router.Router;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Middleware for serving static files from a directory.
 */
public class StaticMiddleware implements Middleware {
    private final Path rootDirectory;
    private final String urlPrefix;
    private final String defaultFileName;
    private final Set<Router> apiRouters;  // Store reference to API routers

    public StaticMiddleware(String rootDirectory, String urlPrefix) {
        this(rootDirectory, urlPrefix, "index.html", new HashSet<>());
    }


    /**
     * Creates a new StaticMiddleware.
     *
     * @param rootDirectory The directory on the server filesystem to serve files from
     * @param urlPrefix     The URL path prefix to mount static files (e.g. "/static")
     */
    public StaticMiddleware(String rootDirectory, String urlPrefix, String defaultFileName, Set<Router> apiRouters) {
        Objects.requireNonNull(rootDirectory, "Static root directory cannot be null");
        Objects.requireNonNull(urlPrefix, "URL prefix cannot be null");
        this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        this.defaultFileName = defaultFileName;
        this.apiRouters = apiRouters;
    }

    @Override
    public void apply(Request req, Response res, MiddlewareChain next) throws IOException {
        String requestPath = req.getPath();

        // Check if any API router matches this path
        for (Router router : apiRouters) {
            if (requestPath.startsWith(router.getPrefix())) {
                next.next(req, res);
                return;
            }
        }

        // Continue with static file serving...
        String relPath = requestPath.startsWith(urlPrefix)
                ? requestPath.substring(urlPrefix.length())
                : requestPath;

        if (relPath.isEmpty() || relPath.endsWith("/")) {
            relPath = relPath + defaultFileName;
        }

        // Rest of your existing static file serving logic...
        handleStaticFile(relPath, req, res, next);
    }

    private void handleStaticFile(String relPath, Request req, Response res, MiddlewareChain next) throws IOException {
        Path filePath = rootDirectory.resolve(relPath).normalize();

        // Security: prevent path traversal attacks
        if (!filePath.startsWith(rootDirectory)) {
            CabinLogger.warn("Attempt to access file outside static root: " + filePath);
            next.next(req, res);
            return;
        }

        // Check if file exists and is not a directory
        if (Files.isDirectory(filePath) || !Files.exists(filePath)) {
            filePath = rootDirectory.resolve(defaultFileName).normalize();
            if (!Files.exists(filePath)) {
                next.next(req, res);
                return;
            }
        }

        serveFile(filePath, res);
    }

    private void serveFile(Path filePath, Response res) throws IOException {
        try {
            String contentType = URLConnection.guessContentTypeFromName(filePath.toString());
            if (contentType != null) {
                res.setHeader("Content-Type", contentType);
            } else {
                res.setHeader("Content-Type", "application/octet-stream");
            }

            res.setStatusCode(200);
            res.setHeader("Cache-Control", "public, max-age=3600");

            try (InputStream in = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    res.write(buffer, 0, read);
                }
            }

            res.send();
        } catch (IOException e) {
            CabinLogger.error("Error serving static file: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Add API routers to be excluded from static file serving
     * @param routers the routers to exclude
     * @return this middleware instance for chaining
     */
    public StaticMiddleware excludeRouters(Router... routers) {
        Collections.addAll(apiRouters, routers);
        return this;
    }
}