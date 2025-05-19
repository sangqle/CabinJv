package com.cabin.express.server;

import com.cabin.express.profiler.ServerProfiler;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfilerConfigurationTest {

    @Test
    void shouldConfigureProfilerInServerBuilder() {
        // Given
        boolean initialState = ServerProfiler.INSTANCE.isEnabled();

        try {
            // When - Create server with profiler enabled
            CabinServer serverWithProfiler = new ServerBuilder()
                    .enableProfiler(true)
                    .setProfilerSamplingInterval(Duration.ofSeconds(2))
                    .build();

            // Then - Profiler should be enabled
            assertThat(ServerProfiler.INSTANCE.isEnabled()).isTrue();
            assertThat(serverWithProfiler.isProfilerEnabled()).isTrue();

            // When - Create server with profiler disabled
            CabinServer serverWithoutProfiler = new ServerBuilder()
                    .enableProfiler(false)
                    .build();

            // Then - Profiler should be disabled
            assertThat(ServerProfiler.INSTANCE.isEnabled()).isFalse();
            assertThat(serverWithoutProfiler.isProfilerEnabled()).isFalse();

            // When - Toggle profiler state
            serverWithoutProfiler.setProfilerEnabled(true);

            // Then - Profiler should now be enabled
            assertThat(ServerProfiler.INSTANCE.isEnabled()).isTrue();
            assertThat(serverWithoutProfiler.isProfilerEnabled()).isTrue();
        } finally {
            // Restore original state
            ServerProfiler.INSTANCE.setEnabled(initialState);
        }
    }
}