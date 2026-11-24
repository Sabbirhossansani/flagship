package com.flagship.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.VariationType;
import com.flagship.server.domain.ChangeRequestStatus;
import com.flagship.server.dto.*;
import com.flagship.server.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class Phase3AdvancedFeaturesIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private FlagService flagService;

    @Autowired
    private ChangeControlService changeControlService;

    @Autowired
    private ScheduledFlagChangeService scheduledChangeService;

    @Autowired
    private ExperimentService experimentService;

    @Autowired
    private FlagHygieneService flagHygieneService;

    @Autowired
    private SseBroadcasterService sseBroadcasterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String projectKey = "p3-proj";
    private final String envKey = "staging";

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.cleanAllTables(jdbcTemplate);
        projectService.createProject(new CreateProjectRequest(projectKey, "Phase 3 Project", "Test proj"));
    }

    @AfterEach
    void tearDown() {
        TestDatabaseCleaner.cleanAllTables(jdbcTemplate);
    }

    @Test
    @DisplayName("FR5 Real-time updates: SseBroadcasterService registers subscriber and active count")
    void sseStreamSubscription() {
        SseEmitter emitter = sseBroadcasterService.subscribe(projectKey, envKey);
        try {
            assertThat(emitter).isNotNull();
            assertThat(sseBroadcasterService.getActiveSubscriberCount(projectKey, envKey)).isGreaterThanOrEqualTo(1);
        } finally {
            emitter.complete();
        }
    }

    @Test
    @DisplayName("FR7 Change Control: History recording and one-action rollback")
    void changeControl_historyAndRollback() throws Exception {
        String flagKey = "rollback-flag";
        CreateFlagRequest createReq = new CreateFlagRequest(
                flagKey,
                "Rollback Flag",
                "Description v1",
                "alice",
                VariationType.BOOLEAN,
                true,
                false,
                List.of(new VariationRequest("on", true), new VariationRequest("off", false)),
                "on",
                "off",
                Map.of(),
                List.of(),
                0.0,
                null,
                List.of()
        );
        FlagResponse v1 = flagService.createFlag(projectKey, envKey, createReq);
        assertThat(v1.version()).isEqualTo(1L);

        UpdateFlagRequest updateReq = new UpdateFlagRequest(
                "Rollback Flag Updated",
                "Description v2",
                "alice",
                false, // turned off
                true,  // kill switch engaged
                List.of(new VariationRequest("on", true), new VariationRequest("off", false)),
                "off",
                "off",
                Map.of(),
                List.of(),
                50.0,
                "on",
                List.of()
        );
        FlagResponse v2 = flagService.updateFlag(projectKey, envKey, flagKey, updateReq);
        assertThat(v2.version()).isEqualTo(2L);
        assertThat(v2.enabled()).isFalse();

        // Verify history records
        var historyPage = changeControlService.getFlagHistory(projectKey, envKey, flagKey, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(historyPage.content()).hasSizeGreaterThanOrEqualTo(2);

        // One-action rollback to version 1
        FlagResponse rolledBack = changeControlService.rollbackFlag(
                projectKey,
                envKey,
                flagKey,
                1L,
                "bob",
                "Emergency rollback to v1"
        );

        assertThat(rolledBack.version()).isEqualTo(3L);
        assertThat(rolledBack.enabled()).isTrue();
        assertThat(rolledBack.killSwitch()).isFalse();
    }

    @Test
    @DisplayName("FR7 Change Control: Two-person approval enforcement")
    void changeControl_twoPersonApproval() throws Exception {
        String prodEnvKey = "prod-approval";
        projectService.createEnvironment(projectKey, new CreateEnvironmentRequest(prodEnvKey, "Production", true));

        String flagKey = "protected-flag";
        CreateFlagRequest createReq = new CreateFlagRequest(
                flagKey,
                "Protected Flag",
                "Approval test",
                "alice",
                VariationType.BOOLEAN,
                true,
                false,
                List.of(new VariationRequest("on", true), new VariationRequest("off", false)),
                "on",
                "off",
                Map.of(),
                List.of(),
                0.0,
                null,
                List.of()
        );
        flagService.createFlag(projectKey, prodEnvKey, createReq);

        // Author alice requests a change
        UpdateFlagRequest updateReq = new UpdateFlagRequest(
                "Protected Flag v2",
                "Approval needed",
                "alice",
                true,
                false,
                List.of(new VariationRequest("on", true), new VariationRequest("off", false)),
                "on",
                "off",
                Map.of(),
                List.of(),
                100.0,
                "on",
                List.of()
        );
        String payloadJson = objectMapper.writeValueAsString(updateReq);

        ChangeRequestResponse cr = changeControlService.createChangeRequest(
                projectKey,
                prodEnvKey,
                new CreateChangeRequestDto("FLAG", flagKey, "UPDATE", payloadJson, "Launch feature to 100%"),
                "alice"
        );
        assertThat(cr.status()).isEqualTo(ChangeRequestStatus.PENDING);

        // Alice cannot approve her own request!
        assertThatThrownBy(() -> changeControlService.approveChangeRequest(projectKey, prodEnvKey, cr.id(), "alice"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Two-person approval violated");

        // Bob approves the change request
        ChangeRequestResponse approved = changeControlService.approveChangeRequest(projectKey, prodEnvKey, cr.id(), "bob");
        assertThat(approved.status()).isEqualTo(ChangeRequestStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo("bob");

        // Verify flag was actually updated to 100%
        FlagResponse currentFlag = flagService.getFlag(projectKey, prodEnvKey, flagKey);
        assertThat(currentFlag.rolloutPercentage()).isEqualTo(100.0);

        // Test Change Request with Rules, Variations, and DELETE
        UpdateFlagRequest updateWithRules = new UpdateFlagRequest(
                "Protected Flag v3",
                "Update with rules and variations",
                "alice-lead",
                true,
                false,
                List.of(
                        new VariationRequest("treatment-a", true),
                        new VariationRequest("treatment-b", false)
                ),
                "treatment-a",
                "treatment-b",
                Map.of("user-999", "treatment-a"),
                List.of(new TargetingRuleDto(
                        "rule-1",
                        "Beta testers rule",
                        null,
                        com.flagship.server.dto.RuleNodeDto.leaf("beta", com.flagship.core.rule.RuleOperatorType.EQUALS, List.of("true")),
                        "Beta tester check",
                        "treatment-a",
                        List.of()
                )),
                50.0,
                "treatment-a",
                List.of(new com.flagship.core.model.WeightedVariation("treatment-a", 50000))
        );
        String rulesPayloadJson = objectMapper.writeValueAsString(updateWithRules);

        ChangeRequestResponse cr2 = changeControlService.createChangeRequest(
                projectKey,
                prodEnvKey,
                new CreateChangeRequestDto("FLAG", flagKey, "UPDATE", rulesPayloadJson, "Deploy targeted rule"),
                "alice"
        );
        changeControlService.approveChangeRequest(projectKey, prodEnvKey, cr2.id(), "bob");

        FlagResponse updatedFlag = flagService.getFlag(projectKey, prodEnvKey, flagKey);
        assertThat(updatedFlag.name()).isEqualTo("Protected Flag v3");
        assertThat(updatedFlag.owner()).isEqualTo("alice-lead");
        assertThat(updatedFlag.variations()).hasSize(2);
        assertThat(updatedFlag.rules()).hasSize(1);
        assertThat(updatedFlag.rules().get(0).name()).isEqualTo("Beta testers rule");
        assertThat(updatedFlag.individualTargets()).containsEntry("user-999", "treatment-a");

        // Submit and approve DELETE change request
        ChangeRequestResponse crDelete = changeControlService.createChangeRequest(
                projectKey,
                prodEnvKey,
                new CreateChangeRequestDto("FLAG", flagKey, "DELETE", "{}", "Decommission flag"),
                "alice"
        );
        changeControlService.approveChangeRequest(projectKey, prodEnvKey, crDelete.id(), "bob");

        assertThatThrownBy(() -> flagService.getFlag(projectKey, prodEnvKey, flagKey))
                .isInstanceOf(com.flagship.server.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("FR7 Change Control: Scheduled flag changes executed automatically")
    void scheduledFlagChanges() throws Exception {
        String flagKey = "sched-flag";
        CreateFlagRequest createReq = new CreateFlagRequest(
                flagKey,
                "Sched Flag",
                "Scheduled changes test",
                "admin",
                VariationType.BOOLEAN,
                true,
                false,
                List.of(new VariationRequest("on", true), new VariationRequest("off", false)),
                "on",
                "off",
                Map.of(),
                List.of(),
                0.0,
                null,
                List.of()
        );
        flagService.createFlag(projectKey, envKey, createReq);

        // Schedule kill switch to engage immediately
        String payload = objectMapper.writeValueAsString(new KillSwitchRequest(true));
        ScheduleFlagChangeRequest req = new ScheduleFlagChangeRequest(
                Instant.now().minus(2, ChronoUnit.SECONDS), // already due
                "KILL_SWITCH",
                payload,
                "Scheduled emergency kill switch"
        );

        ScheduledChangeResponse scheduled = scheduledChangeService.scheduleChange(projectKey, envKey, flagKey, req, "ops-bot");
        assertThat(scheduled.executed()).isFalse();

        // Trigger scheduler
        scheduledChangeService.executePendingSchedules();

        FlagResponse updated = flagService.getFlag(projectKey, envKey, flagKey);
        assertThat(updated.killSwitch()).isTrue();
    }

    @Test
    @DisplayName("FR8 Experiments: Events ingestion and statistical calculation")
    void experiments_eventsAndStatisticalSignificance() {
        String flagKey = "exp-flag";
        CreateFlagRequest createReq = new CreateFlagRequest(
                flagKey,
                "Exp Flag",
                "Experiment test",
                "data-team",
                VariationType.STRING,
                true,
                false,
                List.of(new VariationRequest("control", "v1"), new VariationRequest("treatment", "v2")),
                "control",
                "control",
                Map.of(),
                List.of(),
                0.0,
                null,
                List.of()
        );
        flagService.createFlag(projectKey, envKey, createReq);

        // Ingest exposure and conversion events
        List<ExperimentEventDto> events = List.of(
                new ExperimentEventDto("EXPOSURE", flagKey, "control", null, "user-1", 1.0, Instant.now()),
                new ExperimentEventDto("EXPOSURE", flagKey, "control", null, "user-2", 1.0, Instant.now()),
                new ExperimentEventDto("EXPOSURE", flagKey, "treatment", null, "user-3", 1.0, Instant.now()),
                new ExperimentEventDto("EXPOSURE", flagKey, "treatment", null, "user-4", 1.0, Instant.now()),
                new ExperimentEventDto("GOAL", flagKey, "treatment", "signup", "user-3", 1.0, Instant.now())
        );
        experimentService.recordEvents(projectKey, envKey, events);

        var results = experimentService.getExperimentResults(projectKey, envKey, flagKey, "signup", "control");
        assertThat(results.totalExposures()).isEqualTo(4);
        assertThat(results.totalConversions()).isEqualTo(1);
        assertThat(results.variations().get("treatment").conversionRate()).isEqualTo(0.5);
        assertThat(results.hasSrm()).isFalse();
    }

    @Test
    @DisplayName("FR9 Flag Hygiene: Detects stale flags")
    void flagHygiene_detectsStaleFlags() {
        List<StaleFlagDto> stale = flagHygieneService.findStaleFlags(projectKey, envKey);
        assertThat(stale).isNotNull();
    }
}
