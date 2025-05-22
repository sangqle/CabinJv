package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.loggger.CabinLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class StaticMiddleware implements Middleware {
    private final Path rootDirectory;
    private final String urlPrefix;
    private final String defaultFileName;
    private final Set<String> excludedPrefixes;  // Store URL prefixes to exclude

    /**
     * Creates a new StaticMiddleware with defaults.
     *
     * @param rootDirectory The directory on the server filesystem to serve files from
     * @param urlPrefix     The URL path prefix to mount static files (e.g. "/static")
     */
    public StaticMiddleware(String rootDirectory, String urlPrefix) {
        this(rootDirectory, urlPrefix, "index.html", new HashSet<>());
    }

    /**
     * Creates a new StaticMiddleware.
     *
     * @param rootDirectory  The directory on the server filesystem to serve files from
     * @param urlPrefix      The URL path prefix to mount static files (e.g. "/static")
     * @param defaultFileName Default file to serve for directory requests (e.g. "index.html")
     * @param excludedPrefixes Set of URL prefixes to exclude from static file serving
     */
    public StaticMiddleware(String rootDirectory, String urlPrefix, String defaultFileName, Set<String> excludedPrefixes) {
        this.rootDirectory = Paths.get(rootDirectory).normalize();
        
        // Normalize URL prefix (ensure it starts with '/' and doesn't end with '/')
        this.urlPrefix = normalizeUrlPrefix(urlPrefix);
        
        this.defaultFileName = defaultFileName;
        this.excludedPrefixes = excludedPrefixes;
    }

    @Override
    public void apply(Request req, Response res, MiddlewareChain next) throws IOException {
        String requestPath = req.getPath();
        
        // Skip if request path doesn't start with our URL prefix (unless prefix is '/')
        if (!urlPrefix.equals("/") && !requestPath.startsWith(urlPrefix)) {
            next.next(req, res);
            return;
        }
        
        // Skip if the path matches any excluded prefix
        for (String prefix : excludedPrefixes) {
            if (requestPath.startsWith(prefix)) {
                next.next(req, res);
                return;
            }
        }
        
        // Extract relative path from the URL prefix
        String relPath;
        if (urlPrefix.equals("/")) {
            relPath = requestPath.substring(1); // Remove leading '/'
        } else {
            relPath = requestPath.substring(urlPrefix.length());
            if (relPath.startsWith("/")) {
                relPath = relPath.substring(1);
            }
        }
        
        // Serve directory root
        if (relPath.isEmpty()) {
            relPath = defaultFileName;
        }
        
        // Handle the static file
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
        // Set content type based on file extension
        String contentType = getContentType(filePath.toString());
        res.setHeader("Content-Type", contentType);
        
        // Set content length
        long fileSize = Files.size(filePath);
        res.setHeader("Content-Length", String.valueOf(fileSize));
        
        // Read and send file content
        byte[] fileContent = Files.readAllBytes(filePath);
        res.setStatusCode(200);
        res.writeBody(fileContent);
        res.send();
    }

    /**
     * Add URL prefixes to be excluded from static file serving.
     * Any request path starting with one of these prefixes will be passed to the next middleware.
     * 
     * @param prefixes the URL path prefixes to exclude
     * @return this middleware instance for chaining
     */
    public StaticMiddleware excludePrefixes(String... prefixes) {
        for (String prefix : prefixes) {
            // Ensure prefix starts with '/'
            String normalizedPrefix = prefix.startsWith("/") ? prefix : "/" + prefix;
            excludedPrefixes.add(normalizedPrefix);
        }
        return this;
    }
    
    /**
     * Helper method to get content type based on file extension
     */
    private String getContentType(String path) {
        String extension = "";
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            extension = path.substring(lastDot + 1).toLowerCase();
        }
        
        return switch (extension) {
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "txt" -> "text/plain";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
    
    /**
     * Helper method to normalize URL prefix
     */
    private String normalizeUrlPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "/";
        }
        
        // Ensure it starts with '/'
        prefix = prefix.startsWith("/") ? prefix : "/" + prefix;
        
        // Remove trailing '/' if present and not the root
        if (prefix.length() > 1 && prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        
        return prefix;
    }
}