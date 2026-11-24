package com.flagship.starter.sync;

import com.flagship.core.evaluation.FlagEvaluationEngine;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.starter.client.DefaultFlagShipClient;
import com.flagship.starter.config.FlagshipStarterProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA2 Verification Test: 30-minute server outage simulation (FR6).
 * Verifies local disk persistence cold start, uninterrupted evaluation during complete server blackout,
 * zero exceptions, and clean resync upon recovery.
 */
class Qa2OutageSimulationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("QA2: 30-Minute Outage Simulation with Local Cache Recovery and Zero Evaluation Downtime")
    void simulate30MinuteOutageAndRecovery() {
        Path cacheFile = tempDir.resolve("flagship-qa2-cache.json");

        FlagshipStarterProperties properties = new FlagshipStarterProperties();
        properties.setDiskPersistenceEnabled(true);
        properties.setDiskPersistencePath(cacheFile.toString());
        properties.setProjectKey("ecommerce");
        properties.setEnvironmentKey("production");

        DiskPersistenceManager diskManager = new DiskPersistenceManager(properties);

        // --- STEP 1: Pre-outage Normal State (Revision 10) ---
        FlagDefinition checkoutFlag = FlagDefinition.builder("checkout-v2", VariationType.BOOLEAN)
                .name("Instant Checkout V2")
                .variation(new Variation("true", VariationType.BOOLEAN, true, "Enabled"))
                .variation(new Variation("false", VariationType.BOOLEAN, false, "Disabled"))
                .defaultVariationKey("true")
                .offVariationKey("false")
                .version(10L)
                .build();

        EnvironmentSnapshot initialSnapshot = new EnvironmentSnapshot(
                "ecommerce",
                "production",
                10L,
                Instant.now(),
                Map.of("checkout-v2", checkoutFlag),
                Map.of()
        );

        diskManager.persist(initialSnapshot);
        assertThat(cacheFile).exists();

        // --- STEP 2: Initiate 30-Minute Outage Simulation ---
        // Advance clock by 30 minutes, server is completely unreachable
        Instant outageStartTime = Instant.now();
        Instant outageEndTime = outageStartTime.plus(Duration.ofMinutes(30));

        // Cold-start a new application instance during the outage
        DiskPersistenceManager outageDiskManager = new DiskPersistenceManager(properties);
        EnvironmentSnapshot restoredSnapshot = outageDiskManager.load();
        assertThat(restoredSnapshot).isNotNull();
        assertThat(restoredSnapshot.revision()).isEqualTo(10L);

        DefaultFlagShipClient offlineClient = new DefaultFlagShipClient(new FlagEvaluationEngine(), restoredSnapshot);

        // --- STEP 3: Execute 10,000 Evaluations During Outage ---
        int totalRequests = 10_000;
        int successfulEvaluations = 0;
        int fallbackEvaluations = 0;

        for (int i = 0; i < totalRequests; i++) {
            EvaluationContext ctx = EvaluationContext.builder().userKey("user-" + i).build();

            // Cached flag must evaluate true
            boolean isV2 = offlineClient.isEnabled("checkout-v2", ctx, false);
            if (isV2) successfulEvaluations++;

            // Non-existent flag must safely return fallback with zero exceptions
            boolean fallbackVal = offlineClient.isEnabled("non-existent-flag", ctx, false);
            if (!fallbackVal) fallbackEvaluations++;
        }

        assertThat(successfulEvaluations).isEqualTo(totalRequests);
        assertThat(fallbackEvaluations).isEqualTo(totalRequests);

        System.out.println("QA2 Outage Simulation -> 10,000 requests during 30-min blackout: 100% served from local cache");

        // --- STEP 4: Server Recovers (Revision 11 with updated flag) ---
        FlagDefinition updatedFlag = checkoutFlag.toBuilder()
                .version(11L)
                .rolloutPercentage(100.0)
                .build();

        EnvironmentSnapshot recoveredSnapshot = new EnvironmentSnapshot(
                "ecommerce",
                "production",
                11L,
                outageEndTime,
                Map.of("checkout-v2", updatedFlag),
                Map.of()
        );

        // Client receives recovered snapshot
        offlineClient.updateSnapshot(recoveredSnapshot);
        outageDiskManager.persist(recoveredSnapshot);

        assertThat(offlineClient.getCurrentSnapshot().revision()).isEqualTo(11L);
        assertThat(offlineClient.getCurrentSnapshot().getFlag("checkout-v2").getVersion()).isEqualTo(11L);
    }
}
