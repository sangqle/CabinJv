package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.Objects;

/**
 * Middleware for serving static files from a directory.
 */
public class StaticMiddleware implements Middleware {
    private final Path rootDirectory;
    private final String urlPrefix;
    private final String defaultFileName;

    public StaticMiddleware(String rootDirectory, String urlPrefix) {
        this(rootDirectory, urlPrefix, "index.html");
    }


    /**
     * Creates a new StaticMiddleware.
     *
     * @param rootDirectory The directory on the server filesystem to serve files from
     * @param urlPrefix     The URL path prefix to mount static files (e.g. "/static")
     */
    public StaticMiddleware(String rootDirectory, String urlPrefix, String defaultFileName) {
        Objects.requireNonNull(rootDirectory, "Static root directory cannot be null");
        Objects.requireNonNull(urlPrefix, "URL prefix cannot be null");
        this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
        this.defaultFileName = defaultFileName;
    }

    @Override
    public void apply(Request req, Response res, MiddlewareChain next) throws IOException {
        String requestPath = req.getPath();

        // Skip if not a static file request
        if (!requestPath.startsWith(urlPrefix)) {
            next.next(req, res);
            return;
        }

        // Get relative path (remove prefix)
        String relPath = requestPath.substring(urlPrefix.length());
        // If empty or ends with /, look for defaultFileName
        if (relPath.isEmpty() || relPath.endsWith("/")) {
            relPath = relPath + defaultFileName;
        }

        Path filePath = rootDirectory.resolve(relPath).normalize();

        // Security: prevent path traversal attacks
        if (!filePath.startsWith(rootDirectory)) {
            CabinLogger.warn("Attempt to access file outside static root: " + filePath);
            res.setStatusCode(403);
            res.writeBody("Forbidden");
            res.send();
            return;
        }

        // Check if file exists and is not a directory
        if (Files.isDirectory(filePath) || !Files.exists(filePath)) {
            next.next(req, res); // Not found, pass to next middleware/handler
            return;
        }

        try {
            // Set content type based on file extension
            String contentType = URLConnection.guessContentTypeFromName(filePath.toString());
            if (contentType != null) {
                res.setHeader("Content-Type", contentType);
            } else {
                // Default to binary if type unknown
                res.setHeader("Content-Type", "application/octet-stream");
            }

            res.setStatusCode(200);
            res.setHeader("Cache-Control", "public, max-age=3600");

            // Stream file to response using new write method
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
            res.setStatusCode(500);
            res.writeBody("Internal Server Error");
            res.send();
        }
    }
}