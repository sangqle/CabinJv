package com.cabin.express.integration;

import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.cabin.express.util.ServerTestUtil;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class RouterIntegrationTest {

    private CabinServer server;
    private Router router;
    private Thread serverThread;
    private int TEST_PORT;
    private String BASE_URL;
    
    @BeforeEach
    void setUp() throws IOException {
        // Use dynamic port allocation
        Object[] serverInfo = ServerTestUtil.startServerWithDynamicPort();
        server = (CabinServer) serverInfo[0];
        TEST_PORT = (int) serverInfo[1];
        serverThread = (Thread) serverInfo[2];
        BASE_URL = "http://localhost:" + TEST_PORT;
                
        router = new Router();
        router.setPrefix("/api");
        
        // Setup some test routes
        router.get("/hello", (req, res) -> {
            res.writeBody("Hello World");
            res.send();
        });
        
        router.get("/json", (req, res) -> {
            JsonObject json = new JsonObject();
            json.addProperty("message", "Hello JSON");
            res.send(json);
        });
        
        router.get("/users/:id", (req, res) -> {
            String id = req.getPathParam("id");
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("name", "User " + id);
            res.send(json);
        });
        
        server.use(router);
        
        // Verify server is ready
        boolean isReady = ServerTestUtil.waitForServerReady(BASE_URL, "/api/hello", 5000);
        if (!isReady) {
            fail("Server failed to start in time");
        }
    }
    
    @AfterEach
    void tearDown() {
        if (server != null) {
            boolean stopped = ServerTestUtil.stopServer(server, 5000);
            assertThat(stopped).withFailMessage("Server failed to stop cleanly").isTrue();
        }
    }
    
    @Test
    void shouldReturnTextResponse() throws IOException, InterruptedException {
        // Given
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/hello"))
                .GET()
                .build();
                
        // When
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Hello World");
    }
    
    @Test
    void shouldReturnJsonResponse() throws IOException, InterruptedException {
        // Given
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/json"))
                .GET()
                .build();
                
        // When
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Hello JSON");
    }
    
    @Test
    void shouldHandlePathParameters() throws IOException, InterruptedException {
        // Given
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/users/123"))
                .GET()
                .build();
                
        // When
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":\"123\"");
        assertThat(response.body()).contains("\"name\":\"User 123\"");
    }
    
    @Test
    void shouldReturn404ForUnknownRoute() throws IOException, InterruptedException {
        // Given
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/unknown"))
                .GET()
                .build();
                
        // When
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Then
        assertThat(response.statusCode()).isEqualTo(404);
    }
}