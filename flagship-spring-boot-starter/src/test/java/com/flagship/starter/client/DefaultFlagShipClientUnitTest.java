package com.flagship.starter.client;

import com.flagship.core.evaluation.FlagEvaluationEngine;
import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFlagShipClientUnitTest {

    private final FlagEvaluationEngine engine = new FlagEvaluationEngine();

    private FlagDefinition createFlag(String key, boolean enabled) {
        return FlagDefinition.builder(key, VariationType.BOOLEAN)
                .name(key)
                .enabled(enabled)
                .variation(new Variation("true", VariationType.BOOLEAN, true, null))
                .variation(new Variation("false", VariationType.BOOLEAN, false, null))
                .defaultVariationKey(enabled ? "true" : "false")
                .offVariationKey("false")
                .rolloutPercentage(enabled ? 100.0 : 0.0)
                .rolloutVariationKey("true")
                .version(1L)
                .build();
    }

    @Test
    @DisplayName("DefaultFlagShipClient: Notifies listener when flag is updated or deleted")
    void changeListener_notifiesUpdatesAndDeletions() {
        FlagDefinition flagA = createFlag("flag-a", true);
        FlagDefinition flagB = createFlag("flag-b", true);

        EnvironmentSnapshot snap1 = new EnvironmentSnapshot(
                "proj", "prod", 1L, Instant.now(),
                Map.of("flag-a", flagA, "flag-b", flagB),
                Map.of()
        );

        DefaultFlagShipClient client = new DefaultFlagShipClient(engine, snap1);

        List<String> notifications = new ArrayList<>();
        client.addChangeListener((key, newDef) -> {
            if (newDef == null) {
                notifications.add("DELETED:" + key);
            } else {
                notifications.add("UPDATED:" + key + ":" + newDef.isEnabled());
            }
        });

        // Snap2: flag-a modified, flag-b deleted, flag-c added
        FlagDefinition flagAModified = createFlag("flag-a", false);
        FlagDefinition flagC = createFlag("flag-c", true);
        EnvironmentSnapshot snap2 = new EnvironmentSnapshot(
                "proj", "prod", 2L, Instant.now(),
                Map.of("flag-a", flagAModified, "flag-c", flagC),
                Map.of()
        );

        client.updateSnapshot(snap2);

        assertThat(notifications)
                .contains("UPDATED:flag-a:false")
                .contains("UPDATED:flag-c:true")
                .contains("DELETED:flag-b");
    }

    @Test
    @DisplayName("DefaultFlagShipClient: Faulty listener does not disrupt other listeners")
    void changeListener_exceptionHandling() {
        FlagDefinition flagA = createFlag("flag-a", true);
        EnvironmentSnapshot snap1 = new EnvironmentSnapshot(
                "proj", "prod", 1L, Instant.now(),
                Map.of("flag-a", flagA),
                Map.of()
        );

        DefaultFlagShipClient client = new DefaultFlagShipClient(engine, snap1);

        AtomicInteger healthyCount = new AtomicInteger(0);

        // Faulty listener throws RuntimeException
        client.addChangeListener((key, def) -> {
            throw new RuntimeException("Simulated observer exception");
        });

        // Healthy listener increments counter
        client.addChangeListener((key, def) -> healthyCount.incrementAndGet());

        FlagDefinition flagAMod = createFlag("flag-a", false);
        client.updateSnapshot(new EnvironmentSnapshot(
                "proj", "prod", 2L, Instant.now(),
                Map.of("flag-a", flagAMod),
                Map.of()
        ));

        assertThat(healthyCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("DefaultFlagShipClient: Null snapshot safely returns fallback values")
    void nullSnapshot_returnsFallback() {
        DefaultFlagShipClient client = new DefaultFlagShipClient(engine, null);
        EvaluationContext ctx = EvaluationContext.builder().userKey("user-1").build();

        assertThat(client.isEnabled("any-flag", ctx, true)).isTrue();
        assertThat(client.isEnabled("any-flag", ctx, false)).isFalse();
        assertThat(client.getString("str-flag", ctx, "fallback-val")).isEqualTo("fallback-val");
        assertThat(client.getInteger("int-flag", ctx, 42)).isEqualTo(42);
        assertThat(client.getDouble("double-flag", ctx, 3.14)).isEqualTo(3.14);
    }
}
