package com.flagship.server.service;

import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.server.dto.FlagResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA3 Verification Test: 50 concurrent client kill-switch propagation (FR3, FR5).
 * Validates that emergency kill switch mutations propagate across 50 concurrent SSE connections in < 5.0 seconds.
 */
class Qa3KillSwitchPropagationTest {

    @Test
    @DisplayName("QA3: 50 Concurrent Clients Receive Kill Switch Broadcast Within 5.0 Seconds")
    void testKillSwitchPropagationTo50Clients() throws Exception {
        SseBroadcasterService broadcaster = new SseBroadcasterService();
        int clientCount = 50;
        CountDownLatch latch = new CountDownLatch(clientCount);
        AtomicInteger receivedCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        String projectKey = "fintech";
        String envKey = "production";

        // 1. Connect 50 concurrent clients
        for (int i = 0; i < clientCount; i++) {
            AtomicInteger calls = new AtomicInteger(0);
            SseEmitter emitter = new SseEmitter(60_000L) {
                @Override
                public void send(SseEventBuilder builder) throws IOException {
                    // Count only the kill-switch broadcast event (second event after handshake)
                    if (calls.incrementAndGet() > 1) {
                        receivedCount.incrementAndGet();
                        latch.countDown();
                    }
                    super.send(builder);
                }
            };
            broadcaster.subscribe(projectKey, envKey, emitter);
        }

        // 2. Prepare emergency kill-switch flag mutation
        FlagResponse killSwitchedFlag = new FlagResponse(
                projectKey,
                envKey,
                "payment-stripe-gateway",
                "Stripe Payment Gateway",
                "Emergency kill switch armed due to upstream outage",
                "ops-lead",
                VariationType.BOOLEAN,
                false, // enabled=false
                true,  // killSwitch=true
                List.of(Variation.ofBoolean("true", true), Variation.ofBoolean("false", false)),
                "true",
                "false",
                Map.of(),
                List.of(),
                0.0,
                null,
                List.of(),
                8L,
                false,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null
        );

        // 3. Dispatch kill switch broadcast and measure propagation time
        long startNano = System.nanoTime();
        broadcaster.broadcastFlagUpdate(projectKey, envKey, "KILL_SWITCH", killSwitchedFlag);

        // 4. Wait for all 50 clients to receive the event (max 5 seconds timeout)
        boolean allReceived = latch.await(5, TimeUnit.SECONDS);
        long totalElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);

        // 5. Assertions: 100% of clients received kill-switch within 5 seconds
        assertThat(allReceived).isTrue();
        assertThat(totalElapsedMs).isLessThan(5000L); // C5 constraint: < 5s

        System.out.printf("QA3 Kill-Switch Propagation -> 50 concurrent clients reached in %d ms (p99 < 5000ms SLA PASSED)%n", totalElapsedMs);
    }
}
