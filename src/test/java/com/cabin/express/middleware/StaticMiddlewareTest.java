package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.util.ServerTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StaticMiddlewareTest {

    @TempDir
    Path tempDir;
    
    private StaticMiddleware middleware;
    
    @Mock
    private Request request;
    
    @Mock
    private Response response;
    
    @Mock
    private MiddlewareChain chain;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create a test index.html file
        Files.writeString(tempDir.resolve("index.html"), "<html><body>Test</body></html>");
        
        // Create a test subdirectory
        Files.createDirectory(tempDir.resolve("css"));
        Files.writeString(tempDir.resolve("css").resolve("style.css"), "body { color: red; }");
        
        middleware = new StaticMiddleware(tempDir.toString(), "/");
    }
    
    @Test
    void shouldServeStaticFile() throws IOException {
        // Given
        when(request.getPath()).thenReturn("/css/style.css");
        
        // When
        middleware.apply(request, response, chain);
        
        // Then
        verify(response).setHeader("Content-Type", "text/css");
        verify(response).setStatusCode(200);
        verify(response).send();
        verify(chain, never()).next(request, response);
    }
    
    @Test
    void shouldServeIndexForRootPath() throws IOException {
        // Given
        when(request.getPath()).thenReturn("/");
        
        // When
        middleware.apply(request, response, chain);
        
        // Then
        verify(response).setHeader("Content-Type", "text/html");
        verify(response).setStatusCode(200);
        verify(response).send();
        verify(chain, never()).next(request, response);
    }
    
    @Test
    void shouldPassToNextMiddlewareForExcludedPrefix() throws IOException {
        // Given
        middleware = middleware.excludePrefixes("/api");
        when(request.getPath()).thenReturn("/api/users");
        
        // When
        middleware.apply(request, response, chain);
        
        // Then
        verify(chain).next(request, response);
        verify(response, never()).send();
    }

    @Test
    void shouldServeStaticAndApiRoutesConcurrently() throws IOException, InterruptedException {
        // Create a temporary directory and add some test files
        Path testFilePath = tempDir.resolve("test-file.txt");
        Files.writeString(testFilePath, "Static file content");

        // Create a subdirectory with additional content
        Path subDirPath = tempDir.resolve("sub");
        Files.createDirectories(subDirPath);
        Path nestedFilePath = subDirPath.resolve("nested.html");
        Files.writeString(nestedFilePath, "<html><body>Nested content</body></html>");

        // Setup server with both static middleware and API routes
        StaticMiddleware staticMiddleware = new StaticMiddleware(tempDir.toString(), "/static");
        staticMiddleware.excludePrefixes("/api"); // Important: exclude API routes

        Router apiRouter = new Router();
        apiRouter.get("/data", (req, res) -> {
            res.setHeader("Content-Type", "application/json");
            res.send("{\"message\": \"API data\"}");
        });

        // Set up a test server with both middleware
        CabinServer testServer = new ServerBuilder()
                .setPort(ServerTestUtil.findAvailablePort())
                .build();

        testServer.use(staticMiddleware);
        testServer.use("/api", apiRouter);

        // Start the server
        Thread serverThread = ServerTestUtil.startServerInBackground(testServer);
        String baseUrl = "http://localhost:" + testServer.getPort();

        try {
            // Wait for server to be ready
            boolean isReady = ServerTestUtil.waitForServerReady(baseUrl, "/static/test-file.txt", 5000);
            assertThat(isReady).isTrue();

            // Create HTTP client
            HttpClient client = HttpClient.newHttpClient();

            // Test 1: Access static file
            HttpRequest staticRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/static/test-file.txt"))
                    .GET()
                    .build();

            HttpResponse<String> staticResponse = client.send(
                    staticRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Test 2: Access nested static file
            HttpRequest nestedRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/static/sub/nested.html"))
                    .GET()
                    .build();

            HttpResponse<String> nestedResponse = client.send(
                    nestedRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Test 3: Access API route
            HttpRequest apiRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/data"))
                    .GET()
                    .build();

            HttpResponse<String> apiResponse = client.send(
                    apiRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Assertions
            // Static file should be served correctly
            assertThat(staticResponse.statusCode()).isEqualTo(200);
            assertThat(staticResponse.body()).isEqualTo("Static file content");

            // Nested file should be served correctly
            assertThat(nestedResponse.statusCode()).isEqualTo(200);
            assertThat(nestedResponse.body()).isEqualTo("<html><body>Nested content</body></html>");

            // API route should work alongside static files
            assertThat(apiResponse.statusCode()).isEqualTo(200);
            assertThat(apiResponse.body()).isEqualTo("{\"message\": \"API data\"}");

        } finally {
            // Clean up
            boolean stopped = ServerTestUtil.stopServer(testServer, 5000);
            assertThat(stopped).isTrue();
        }
    }
}
