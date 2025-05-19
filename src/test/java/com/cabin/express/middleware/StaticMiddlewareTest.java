package com.cabin.express.middleware;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    
//    @Test
//    void shouldPassToNextMiddlewareForNonExistentFile() throws IOException {
//        // Given
//        when(request.getPath()).thenReturn("/not-found.html");
//
//        // When
//        middleware.apply(request, response, chain);
//
//        // Then
//        verify(chain).next(request, response);
//        verify(response, never()).send();
//    }
}
