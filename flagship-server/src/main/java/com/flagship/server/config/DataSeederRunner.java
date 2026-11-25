package com.flagship.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.RuleOperatorType;
import com.flagship.server.domain.*;
import com.flagship.server.dto.RuleNodeDto;
import com.flagship.server.dto.UpdateFlagRequest;
import com.flagship.server.dto.VariationRequest;
import com.flagship.server.service.DomainJsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Idempotent startup seeder for FlagShip platform.
 * Populates default workspace, production environment, flags, segments,
 * audit history, approval requests, and A/B experiment data when starting
 * in non-test profiles (e.g., Docker Compose, local development).
 */
@Component
@Profile("!test")
public class DataSeederRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeederRunner.class);

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagRepository flagRepository;
    private final SegmentRepository segmentRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final FlagChangeHistoryRepository historyRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentEventRepository eventRepository;
    private final DomainJsonCodec jsonCodec;
    private final ObjectMapper objectMapper;

    public DataSeederRunner(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            FlagRepository flagRepository,
            SegmentRepository segmentRepository,
            ChangeRequestRepository changeRequestRepository,
            FlagChangeHistoryRepository historyRepository,
            ExperimentRepository experimentRepository,
            ExperimentEventRepository eventRepository,
            DomainJsonCodec jsonCodec,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.flagRepository = flagRepository;
        this.segmentRepository = segmentRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.historyRepository = historyRepository;
        this.experimentRepository = experimentRepository;
        this.eventRepository = eventRepository;
        this.jsonCodec = jsonCodec;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (projectRepository.existsByProjectKey("default")) {
            log.info("Default project already present in database, skipping seeding.");
            return;
        }

        log.info("Initializing FlagShip platform with default seed data...");
        Instant now = Instant.now();

        // 1. Seed Project & Environments
        ProjectEntity project = new ProjectEntity("default", "Default Project", "Default FlagShip workspace", now);
        EnvironmentEntity devEnv = new EnvironmentEntity(project, "development", "Development", false, now);
        EnvironmentEntity stagingEnv = new EnvironmentEntity(project, "staging", "Staging", false, now);
        EnvironmentEntity prodEnv = new EnvironmentEntity(project, "production", "Production", false, now);

        project.addEnvironment(devEnv);
        project.addEnvironment(stagingEnv);
        project.addEnvironment(prodEnv);
        project = projectRepository.save(project);

        // 2. Seed Segments in production
        SegmentEntity enterpriseSegment = new SegmentEntity();
        enterpriseSegment.setProject(project);
        enterpriseSegment.setEnvKey("production");
        enterpriseSegment.setSegmentKey("enterprise-beta-users");
        enterpriseSegment.setName("Enterprise Beta Users");
        enterpriseSegment.setDescription("Users on enterprise tier with beta opt-in");
        enterpriseSegment.setIncludedUsersJson(jsonCodec.writeStringSet(Set.of("corp-1", "corp-2", "corp-3")));
        enterpriseSegment.setExcludedUsersJson("[]");
        enterpriseSegment.setVersion(1L);
        enterpriseSegment.setCreatedAt(now.minus(Duration.ofDays(5)));
        enterpriseSegment.setUpdatedAt(now.minus(Duration.ofDays(5)));
        segmentRepository.save(enterpriseSegment);

        SegmentEntity euSegment = new SegmentEntity();
        euSegment.setProject(project);
        euSegment.setEnvKey("production");
        euSegment.setSegmentKey("eu-gdpr-audience");
        euSegment.setName("EU GDPR Jurisdiction");
        euSegment.setDescription("Users located in EU geographic boundary");
        RuleNodeDto euCondition = RuleNodeDto.leaf(
                "country",
                RuleOperatorType.IN,
                List.of("DE", "FR", "NL", "IE", "ES", "IT")
        );
        euSegment.setConditionJson(jsonCodec.writeRuleNode(euCondition));
        euSegment.setIncludedUsersJson("[]");
        euSegment.setExcludedUsersJson("[]");
        euSegment.setVersion(1L);
        euSegment.setCreatedAt(now.minus(Duration.ofDays(5)));
        euSegment.setUpdatedAt(now.minus(Duration.ofDays(5)));
        segmentRepository.save(euSegment);

        // 3. Seed Feature Flags in production
        // Flag 1: checkout-v2 (Active, 50% rollout)
        FlagEntity checkoutFlag = new FlagEntity();
        checkoutFlag.setProject(project);
        checkoutFlag.setEnvKey("production");
        checkoutFlag.setFlagKey("checkout-v2");
        checkoutFlag.setName("Instant Checkout V2");
        checkoutFlag.setDescription("Streamlined one-click checkout experience");
        checkoutFlag.setOwner("sani@flagship.io");
        checkoutFlag.setVariationType(VariationType.BOOLEAN);
        checkoutFlag.setEnabled(true);
        checkoutFlag.setKillSwitch(false);
        List<Variation> boolVars = List.of(
                new Variation("true", VariationType.BOOLEAN, true, "Checkout V2 Active"),
                new Variation("false", VariationType.BOOLEAN, false, "Legacy Checkout")
        );
        checkoutFlag.setVariationsJson(jsonCodec.writeVariations(boolVars));
        checkoutFlag.setDefaultVariationKey("true");
        checkoutFlag.setOffVariationKey("false");
        checkoutFlag.setRolloutPercentage(50.0);
        checkoutFlag.setRolloutVariationKey("true");
        List<WeightedVariation> checkoutWeights = List.of(
                new WeightedVariation("true", 50000),
                new WeightedVariation("false", 50000)
        );
        checkoutFlag.setPercentageRolloutJson(jsonCodec.writeWeightedVariations(checkoutWeights));
        checkoutFlag.setVersion(4L);
        checkoutFlag.setCreatedAt(now.minus(Duration.ofDays(3)));
        checkoutFlag.setUpdatedAt(now.minus(Duration.ofHours(3)));
        checkoutFlag.setLastEvaluatedAt(now.minus(Duration.ofMinutes(10)));
        flagRepository.save(checkoutFlag);

        // Flag 2: recommendation-engine-algo (Active, 20% rollout)
        FlagEntity recFlag = new FlagEntity();
        recFlag.setProject(project);
        recFlag.setEnvKey("production");
        recFlag.setFlagKey("recommendation-engine-algo");
        recFlag.setName("ML Recommendation Model");
        recFlag.setDescription("Deep learning item similarity ranking model");
        recFlag.setOwner("ml-team@flagship.io");
        recFlag.setVariationType(VariationType.STRING);
        recFlag.setEnabled(true);
        recFlag.setKillSwitch(false);
        List<Variation> stringVars = List.of(
                new Variation("v1-collaborative", VariationType.STRING, "v1-collaborative", "Collaborative Filtering"),
                new Variation("v2-neural", VariationType.STRING, "v2-neural", "Two-Tower Neural Embeddings")
        );
        recFlag.setVariationsJson(jsonCodec.writeVariations(stringVars));
        recFlag.setDefaultVariationKey("v1-collaborative");
        recFlag.setOffVariationKey("v1-collaborative");
        recFlag.setRolloutPercentage(20.0);
        recFlag.setRolloutVariationKey("v2-neural");
        List<WeightedVariation> recWeights = List.of(
                new WeightedVariation("v2-neural", 20000),
                new WeightedVariation("v1-collaborative", 80000)
        );
        recFlag.setPercentageRolloutJson(jsonCodec.writeWeightedVariations(recWeights));
        recFlag.setVersion(2L);
        recFlag.setCreatedAt(now.minus(Duration.ofDays(7)));
        recFlag.setUpdatedAt(now.minus(Duration.ofDays(2)));
        recFlag.setLastEvaluatedAt(now.minus(Duration.ofHours(1)));
        flagRepository.save(recFlag);

        // Flag 3: payment-gateway-stripe (Kill-Switched)
        FlagEntity stripeFlag = new FlagEntity();
        stripeFlag.setProject(project);
        stripeFlag.setEnvKey("production");
        stripeFlag.setFlagKey("payment-gateway-stripe");
        stripeFlag.setName("Emergency Stripe Killswitch");
        stripeFlag.setDescription("Circuit breaker for Stripe payment integration");
        stripeFlag.setOwner("fintech-team@flagship.io");
        stripeFlag.setVariationType(VariationType.BOOLEAN);
        stripeFlag.setEnabled(false);
        stripeFlag.setKillSwitch(true);
        stripeFlag.setVariationsJson(jsonCodec.writeVariations(boolVars));
        stripeFlag.setDefaultVariationKey("false");
        stripeFlag.setOffVariationKey("false");
        stripeFlag.setRolloutPercentage(0.0);
        stripeFlag.setVersion(7L);
        stripeFlag.setCreatedAt(now.minus(Duration.ofDays(10)));
        stripeFlag.setUpdatedAt(now.minus(Duration.ofHours(2)));
        stripeFlag.setLastEvaluatedAt(now.minus(Duration.ofMinutes(5)));
        flagRepository.save(stripeFlag);

        // Flag 4: legacy-coupon-banner (Stale Flag for FR9 Hygiene tab)
        FlagEntity staleFlag = new FlagEntity();
        staleFlag.setProject(project);
        staleFlag.setEnvKey("production");
        staleFlag.setFlagKey("legacy-coupon-banner");
        staleFlag.setName("Legacy Black Friday Banner");
        staleFlag.setDescription("Temporary promotion banner from Q4 campaign");
        staleFlag.setOwner("growth@flagship.io");
        staleFlag.setVariationType(VariationType.BOOLEAN);
        staleFlag.setEnabled(true);
        staleFlag.setKillSwitch(false);
        staleFlag.setVariationsJson(jsonCodec.writeVariations(boolVars));
        staleFlag.setDefaultVariationKey("true");
        staleFlag.setOffVariationKey("false");
        staleFlag.setRolloutPercentage(100.0);
        staleFlag.setRolloutVariationKey("true");
        staleFlag.setPercentageRolloutJson(jsonCodec.writeWeightedVariations(List.of(new WeightedVariation("true", 100000))));
        staleFlag.setVersion(1L);
        staleFlag.setCreatedAt(now.minus(Duration.ofDays(45)));
        staleFlag.setUpdatedAt(now.minus(Duration.ofDays(40)));
        staleFlag.setLastEvaluatedAt(now.minus(Duration.ofDays(35)));
        staleFlag.setFullRolloutSince(now.minus(Duration.ofDays(22)));
        flagRepository.save(staleFlag);

        // 4. Seed Audit History
        saveAudit("checkout-v2", 1L, "alice@flagship.io", "Initial feature flag definition", "CREATE", now.minus(Duration.ofDays(3)));
        saveAudit("checkout-v2", 2L, "alice@flagship.io", "Targeting 20% canary rollout", "UPDATE", now.minus(Duration.ofDays(1)));
        saveAudit("checkout-v2", 3L, "alice@flagship.io", "Rollout incremented to 50%", "UPDATE", now.minus(Duration.ofHours(12)));
        saveAudit("checkout-v2", 4L, "alice@flagship.io", "Evaluation context rules updated", "UPDATE", now.minus(Duration.ofHours(3)));
        saveAudit("recommendation-engine-algo", 1L, "ml-team@flagship.io", "Model routing flag created", "CREATE", now.minus(Duration.ofDays(7)));
        saveAudit("recommendation-engine-algo", 2L, "ml-team@flagship.io", "Neural network variation enabled", "UPDATE", now.minus(Duration.ofDays(2)));
        saveAudit("payment-gateway-stripe", 6L, "ops-lead", "Stripe payment route active", "UPDATE", now.minus(Duration.ofHours(5)));
        saveAudit("payment-gateway-stripe", 7L, "ops-lead", "Upstream gateway outage mitigation", "TRIGGER_KILL_SWITCH", now.minus(Duration.ofHours(2)));

        // 5. Seed Two-Person Approval Request in production
        try {
            UpdateFlagRequest updateReq = new UpdateFlagRequest(
                    "Instant Checkout V2",
                    "Scale rollout to 100% for peak promotional traffic",
                    "sani@flagship.io",
                    true,
                    false,
                    List.of(new VariationRequest("true", true), new VariationRequest("false", false)),
                    "true",
                    "false",
                    Map.of(),
                    List.of(),
                    100.0,
                    "true",
                    List.of(new WeightedVariation("true", 100000))
            );
            ChangeRequestEntity changeReq = new ChangeRequestEntity();
            changeReq.setProjectKey("default");
            changeReq.setEnvKey("production");
            changeReq.setEntityType("FLAG");
            changeReq.setEntityKey("checkout-v2");
            changeReq.setAction("UPDATE");
            changeReq.setAuthor("developer-bob");
            changeReq.setReason("Increase rollout from 50% to 100% for peak sale");
            changeReq.setStatus(ChangeRequestStatus.PENDING);
            changeReq.setPayloadJson(objectMapper.writeValueAsString(updateReq));
            changeReq.setCreatedAt(now.minus(Duration.ofHours(1)));
            changeReq.setUpdatedAt(now.minus(Duration.ofHours(1)));
            changeRequestRepository.save(changeReq);
        } catch (Exception ex) {
            log.warn("Could not serialize seed change request payload", ex);
        }

        // 6. Seed A/B Experiment & Conversion Events
        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setProjectKey("default");
        experiment.setEnvKey("production");
        experiment.setExperimentKey("checkout-v2-experiment");
        experiment.setName("Checkout V2 Conversion Experiment");
        experiment.setFlagKey("checkout-v2");
        experiment.setMetricName("purchase_completed");
        experiment.setBaselineVariationKey("false");
        experiment.setStatus(ExperimentStatus.RUNNING);
        experiment.setCreatedAt(now.minus(Duration.ofDays(2)));
        experiment.setUpdatedAt(now.minus(Duration.ofDays(2)));
        experimentRepository.save(experiment);

        // Seed balanced exposure events (100 control, 100 treatment) and conversions (5 vs 15)
        List<ExperimentEventEntity> events = new ArrayList<>();
        // Control: 100 exposures, 5 purchases
        for (int i = 1; i <= 100; i++) {
            events.add(createEvent("checkout-v2", "false", "EXPOSURE", null, "user-ctrl-" + i, now.minus(Duration.ofHours(i % 40))));
        }
        for (int i = 1; i <= 5; i++) {
            events.add(createEvent("checkout-v2", "false", "GOAL", "purchase_completed", "user-ctrl-" + i, now.minus(Duration.ofHours(i % 20))));
        }

        // Treatment: 100 exposures, 15 purchases
        for (int i = 1; i <= 100; i++) {
            events.add(createEvent("checkout-v2", "true", "EXPOSURE", null, "user-treat-" + i, now.minus(Duration.ofHours(i % 40))));
        }
        for (int i = 1; i <= 15; i++) {
            events.add(createEvent("checkout-v2", "true", "GOAL", "purchase_completed", "user-treat-" + i, now.minus(Duration.ofHours(i % 20))));
        }
        eventRepository.saveAll(events);

        log.info("FlagShip platform seeded successfully: 4 flags, 2 segments, 1 experiment, 1 pending approval, audit history.");
    }

    private void saveAudit(String flagKey, long version, String author, String reason, String action, Instant time) {
        FlagChangeHistoryEntity history = new FlagChangeHistoryEntity();
        history.setProjectKey("default");
        history.setEnvKey("production");
        history.setEntityType("FLAG");
        history.setEntityKey(flagKey);
        history.setVersion(version);
        history.setAuthor(author);
        history.setReason(reason);
        history.setAction(action);
        history.setDiffSummary("Mutation on " + flagKey + ": " + reason);
        history.setCreatedAt(time);
        historyRepository.save(history);
    }

    private ExperimentEventEntity createEvent(String flagKey, String variationKey, String eventType, String metricName, String userKey, Instant time) {
        ExperimentEventEntity e = new ExperimentEventEntity();
        e.setProjectKey("default");
        e.setEnvKey("production");
        e.setFlagKey(flagKey);
        e.setVariationKey(variationKey);
        e.setEventType(eventType);
        e.setMetricName(metricName);
        e.setUserKey(userKey);
        e.setEventValue(1.0);
        e.setOccurredAt(time);
        e.setCreatedAt(time);
        return e;
    }
}
