package com.flagship.server.service;

import com.flagship.server.domain.FlagEntity;
import com.flagship.server.domain.FlagRepository;
import com.flagship.server.dto.StaleFlagDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlagHygieneService {

    private static final Logger log = LoggerFactory.getLogger(FlagHygieneService.class);

    private static final int INACTIVE_DAYS_THRESHOLD = 30;
    private static final int FULLY_ROLLED_OUT_DAYS_THRESHOLD = 14;

    private final FlagRepository flagRepository;
    private final ProjectService projectService;

    public FlagHygieneService(FlagRepository flagRepository, ProjectService projectService) {
        this.flagRepository = flagRepository;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public List<StaleFlagDto> findStaleFlags(String projectKey, String envKey) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        List<FlagEntity> flags = flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(projectKey, envKey);
        Instant now = Instant.now();
        List<StaleFlagDto> staleFlags = new ArrayList<>();

        for (FlagEntity flag : flags) {
            StaleFlagDto dto = evaluateStaleness(projectKey, envKey, flag, now);
            if (dto != null) {
                staleFlags.add(dto);
            }
        }
        return staleFlags;
    }

    @Transactional(readOnly = true)
    public List<StaleFlagDto> findStaleFlagsForProject(String projectKey) {
        projectService.requireProjectEntity(projectKey);
        List<FlagEntity> flags = flagRepository.findAllByProject_ProjectKeyOrderByFlagKeyAsc(projectKey);
        Instant now = Instant.now();
        List<StaleFlagDto> staleFlags = new ArrayList<>();

        for (FlagEntity flag : flags) {
            StaleFlagDto dto = evaluateStaleness(projectKey, flag.getEnvKey(), flag, now);
            if (dto != null) {
                staleFlags.add(dto);
            }
        }
        return staleFlags;
    }

    @Scheduled(cron = "${flagship.hygiene.cron:0 0 2 * * *}")
    @Transactional(readOnly = true)
    public void runHygieneScanJob() {
        log.info("Starting FlagShip flag hygiene scan job...");
        List<FlagEntity> allFlags = flagRepository.findAll();
        Instant now = Instant.now();
        int staleCount = 0;

        for (FlagEntity flag : allFlags) {
            StaleFlagDto stale = evaluateStaleness(flag.getProject().getProjectKey(), flag.getEnvKey(), flag, now);
            if (stale != null) {
                staleCount++;
                log.warn("FLAG HYGIENE WARNING: Stale flag detected: key='{}', project='{}', env='{}', owner='{}', reason='{}'",
                        flag.getFlagKey(), flag.getProject().getProjectKey(), flag.getEnvKey(), flag.getOwner(), stale.staleReason());
            }
        }
        log.info("Flag hygiene scan complete. Detected {} stale flags.", staleCount);
    }

    public StaleFlagDto evaluateStaleness(String projectKey, String envKey, FlagEntity flag, Instant now) {
        // Criterion 1: 100% rolled out for >= 14 days
        if (flag.getFullRolloutSince() != null) {
            long daysFullyRolledOut = Duration.between(flag.getFullRolloutSince(), now).toDays();
            if (daysFullyRolledOut >= FULLY_ROLLED_OUT_DAYS_THRESHOLD) {
                return new StaleFlagDto(
                        projectKey,
                        envKey,
                        flag.getFlagKey(),
                        flag.getName(),
                        flag.getOwner(),
                        "FULLY_ROLLED_OUT_14_DAYS",
                        calculateDaysInactive(flag, now),
                        daysFullyRolledOut,
                        flag.getLastEvaluatedAt(),
                        flag.getFullRolloutSince(),
                        "Flag is 100% rolled out for > 14 days. Safe to remove conditional logic from codebase."
                );
            }
        }

        // Criterion 2: Not evaluated in >= 30 days
        Instant referenceEvaluationTime = flag.getLastEvaluatedAt() != null
                ? flag.getLastEvaluatedAt()
                : flag.getCreatedAt();

        if (referenceEvaluationTime != null) {
            long daysInactive = Duration.between(referenceEvaluationTime, now).toDays();
            if (daysInactive >= INACTIVE_DAYS_THRESHOLD) {
                return new StaleFlagDto(
                        projectKey,
                        envKey,
                        flag.getFlagKey(),
                        flag.getName(),
                        flag.getOwner(),
                        "NOT_EVALUATED_IN_30_DAYS",
                        daysInactive,
                        flag.getFullRolloutSince() != null ? Duration.between(flag.getFullRolloutSince(), now).toDays() : null,
                        flag.getLastEvaluatedAt(),
                        flag.getFullRolloutSince(),
                        "Flag has not been evaluated in over 30 days. Investigate if feature is dead or abandoned."
                );
            }
        }

        return null;
    }

    private Long calculateDaysInactive(FlagEntity flag, Instant now) {
        Instant ref = flag.getLastEvaluatedAt() != null ? flag.getLastEvaluatedAt() : flag.getCreatedAt();
        return ref != null ? Duration.between(ref, now).toDays() : 0L;
    }
}
