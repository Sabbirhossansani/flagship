package com.flagship.starter.client;

import com.flagship.core.model.EvaluationContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientFlagShipClientTest {

    @Test
    @DisplayName("Resilient client catches runtime errors and safely returns fallback value")
    void catchesException_returnsFallback() {
        FlagShipClient failingDelegate = mock(FlagShipClient.class);
        when(failingDelegate.isEnabled(eq("failing-flag"), any(), eq(false)))
                .thenThrow(new IllegalStateException("Simulated engine crash"));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ResilientFlagShipClient resilient = new ResilientFlagShipClient(failingDelegate, registry);

        boolean result = resilient.isEnabled("failing-flag", false);
        assertThat(result).isFalse();

        // Verify fallback counter incremented
        double fallbacks = registry.counter("flagship.evaluation.fallback", "flag", "failing-flag", "exception", "IllegalStateException").count();
        assertThat(fallbacks).isEqualTo(1.0);
    }
}
