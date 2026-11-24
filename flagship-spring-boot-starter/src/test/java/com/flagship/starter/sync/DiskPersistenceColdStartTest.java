package com.flagship.starter.sync;

import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.starter.config.FlagshipStarterProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiskPersistenceColdStartTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("FR6 Disk Persistence: Persists snapshot and loads on cold start")
    void persistAndColdStartLoad() {
        Path cacheFile = tempDir.resolve("test-flag-cache.json");

        FlagshipStarterProperties properties = new FlagshipStarterProperties();
        properties.setDiskPersistenceEnabled(true);
        properties.setDiskPersistencePath(cacheFile.toString());

        DiskPersistenceManager manager = new DiskPersistenceManager(properties);

        FlagDefinition flag = FlagDefinition.builder("checkout-v2", VariationType.BOOLEAN)
                .name("Checkout V2")
                .description("Testing disk cache")
                .variation(new Variation("on", VariationType.BOOLEAN, true, null))
                .defaultVariationKey("on")
                .offVariationKey("on")
                .rolloutPercentage(100.0)
                .rolloutVariationKey("on")
                .version(5L)
                .build();

        EnvironmentSnapshot snapshot = new EnvironmentSnapshot(
                "ecommerce",
                "production",
                42L,
                Instant.now(),
                Map.of("checkout-v2", flag),
                Map.of()
        );

        // 1. Persist to disk
        manager.persist(snapshot);
        assertThat(cacheFile).exists();

        // 2. Cold-start load with new manager instance
        DiskPersistenceManager coldManager = new DiskPersistenceManager(properties);
        EnvironmentSnapshot loaded = coldManager.load();

        assertThat(loaded).isNotNull();
        assertThat(loaded.projectKey()).isEqualTo("ecommerce");
        assertThat(loaded.environmentKey()).isEqualTo("production");
        assertThat(loaded.revision()).isEqualTo(42L);
        assertThat(loaded.flags()).containsKey("checkout-v2");
        assertThat(loaded.flags().get("checkout-v2").getVersion()).isEqualTo(5L);
    }
}
