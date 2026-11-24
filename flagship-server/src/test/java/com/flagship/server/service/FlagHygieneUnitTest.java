package com.flagship.server.service;

import com.flagship.server.domain.FlagEntity;
import com.flagship.server.dto.StaleFlagDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FlagHygieneUnitTest {

    private final FlagHygieneService service = new FlagHygieneService(null, null);

    @Test
    @DisplayName("Hygiene: Flag 100% rolled out for > 14 days is flagged as stale")
    void fullyRolledOut14Days_stale() {
        Instant now = Instant.now();
        FlagEntity flag = new FlagEntity();
        flag.setFlagKey("checkout-v2");
        flag.setName("Checkout V2");
        flag.setOwner("sani@flagship.io");
        flag.setFullRolloutSince(now.minus(15, ChronoUnit.DAYS));
        flag.setLastEvaluatedAt(now.minus(1, ChronoUnit.HOURS));

        StaleFlagDto result = service.evaluateStaleness("proj", "prod", flag, now);

        assertThat(result).isNotNull();
        assertThat(result.staleReason()).isEqualTo("FULLY_ROLLED_OUT_14_DAYS");
        assertThat(result.owner()).isEqualTo("sani@flagship.io");
        assertThat(result.suggestedAction()).contains("remove conditional logic");
    }

    @Test
    @DisplayName("Hygiene: Flag not evaluated in > 30 days is flagged as stale")
    void notEvaluated30Days_stale() {
        Instant now = Instant.now();
        FlagEntity flag = new FlagEntity();
        flag.setFlagKey("legacy-banner");
        flag.setName("Legacy Banner");
        flag.setOwner("marketing-team");
        flag.setFullRolloutSince(null);
        flag.setLastEvaluatedAt(now.minus(32, ChronoUnit.DAYS));

        StaleFlagDto result = service.evaluateStaleness("proj", "prod", flag, now);

        assertThat(result).isNotNull();
        assertThat(result.staleReason()).isEqualTo("NOT_EVALUATED_IN_30_DAYS");
        assertThat(result.owner()).isEqualTo("marketing-team");
    }

    @Test
    @DisplayName("Hygiene: Recently evaluated flag with 50% rollout is healthy")
    void activeFlag_healthy() {
        Instant now = Instant.now();
        FlagEntity flag = new FlagEntity();
        flag.setFlagKey("ai-recommendations");
        flag.setName("AI Recs");
        flag.setOwner("data-team");
        flag.setFullRolloutSince(null);
        flag.setLastEvaluatedAt(now.minus(2, ChronoUnit.HOURS));

        StaleFlagDto result = service.evaluateStaleness("proj", "prod", flag, now);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Hygiene: Uninitialized flag entity with null createdAt and lastEvaluatedAt does not throw NPE")
    void uninitializedFlag_doesNotThrowNpe() {
        Instant now = Instant.now();
        FlagEntity flag = new FlagEntity();
        flag.setFlagKey("new-unpersisted-flag");
        flag.setName("New Flag");
        flag.setOwner("alice");
        flag.setCreatedAt(null);
        flag.setLastEvaluatedAt(null);
        flag.setFullRolloutSince(null);

        StaleFlagDto result = service.evaluateStaleness("proj", "prod", flag, now);

        assertThat(result).isNull();
    }
}
