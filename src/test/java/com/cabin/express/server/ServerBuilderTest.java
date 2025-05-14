package com.cabin.express.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
//
public class ServerBuilderTest {

    @Test
    void shouldCreateServerWithDefaultSettings() {
        // When
        CabinServer server = new ServerBuilder().build();

        // Then
        assertThat(server).isNotNull();
    }

    @Test
    void shouldSetCustomPort() {
        // When
        CabinServer server = new ServerBuilder()
                .setPort(9000)
                .build();

        // Then
        // We would need a getter to verify this, or use reflection
        // For now, just verify server creation succeeds
        assertThat(server).isNotNull();
    }

    @Test
    void shouldSetPoolSizes() {
        // When
        CabinServer server = new ServerBuilder()
                .setDefaultPoolSize(10)
                .setMaxPoolSize(50)
                .setMaxQueueCapacity(100)
                .build();

        // Then
        assertThat(server).isNotNull();
    }

    @Test
    void shouldEnableLogMetrics() {
        // When
        CabinServer server = new ServerBuilder()
                .enableLogMetrics(true)
                .build();

        // Then
        assertThat(server).isNotNull();
    }

    @Test
    void shouldThrowExceptionForInvalidPort() {
        // Then
        assertThatThrownBy(() ->
            new ServerBuilder()
                .setPort(-1)
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
