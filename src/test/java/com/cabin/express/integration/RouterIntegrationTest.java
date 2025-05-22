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

}