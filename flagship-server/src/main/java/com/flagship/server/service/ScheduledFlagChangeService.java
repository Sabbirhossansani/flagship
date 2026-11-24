package com.flagship.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.server.domain.*;
import com.flagship.server.dto.KillSwitchRequest;
import com.flagship.server.dto.ScheduleFlagChangeRequest;
import com.flagship.server.dto.ScheduledChangeResponse;
import com.flagship.server.dto.UpdateFlagRequest;
import com.flagship.server.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ScheduledFlagChangeService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledFlagChangeService.class);

    private final ScheduledFlagChangeRepository scheduledChangeRepository;
    private final FlagRepository flagRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagChangeHistoryRepository historyRepository;
    private final ProjectService projectService;
    private final DomainJsonCodec jsonCodec;
    private final SseBroadcasterService sseBroadcasterService;
    private final ObjectMapper objectMapper;

    public ScheduledFlagChangeService(
            ScheduledFlagChangeRepository scheduledChangeRepository,
            FlagRepository flagRepository,
            EnvironmentRepository environmentRepository,
            FlagChangeHistoryRepository historyRepository,
            ProjectService projectService,
            DomainJsonCodec jsonCodec,
            SseBroadcasterService sseBroadcasterService,
            ObjectMapper objectMapper
    ) {
        this.scheduledChangeRepository = scheduledChangeRepository;
        this.flagRepository = flagRepository;
        this.environmentRepository = environmentRepository;
        this.historyRepository = historyRepository;
        this.projectService = projectService;
        this.jsonCodec = jsonCodec;
        this.sseBroadcasterService = sseBroadcasterService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScheduledChangeResponse scheduleChange(
            String projectKey,
            String envKey,
            String flagKey,
            ScheduleFlagChangeRequest request,
            String author
    ) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Flag '" + flagKey + "' not found"));

        ScheduledFlagChangeEntity entity = new ScheduledFlagChangeEntity();
        entity.setProjectKey(projectKey);
        entity.setEnvKey(envKey);
        entity.setFlagKey(flagKey);
        entity.setAction(request.action().toUpperCase());
        entity.setPayloadJson(request.payloadJson());
        entity.setAuthor(author != null ? author : "system");
        entity.setReason(request.reason());
        entity.setScheduledTime(request.scheduledTime());
        entity.setExecuted(false);
        entity.setCreatedAt(Instant.now());

        ScheduledFlagChangeEntity saved = scheduledChangeRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ScheduledChangeResponse> listSchedules(String projectKey, String envKey, String flagKey) {
        return scheduledChangeRepository
                .findAllByProjectKeyAndEnvKeyAndFlagKeyOrderByScheduledTimeAsc(projectKey, envKey, flagKey)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void cancelSchedule(String projectKey, String envKey, String flagKey, Long id) {
        ScheduledFlagChangeEntity entity = scheduledChangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled change #" + id + " not found"));
        if (!entity.getProjectKey().equals(projectKey) || !entity.getEnvKey().equals(envKey) || !entity.getFlagKey().equals(flagKey)) {
            throw new ResourceNotFoundException("Scheduled change #" + id + " does not match specified flag");
        }
        scheduledChangeRepository.delete(entity);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void executePendingSchedules() {
        Instant now = Instant.now();
        List<ScheduledFlagChangeEntity> pending =
                scheduledChangeRepository.findAllByExecutedFalseAndScheduledTimeLessThanEqualOrderByScheduledTimeAsc(now);

        for (ScheduledFlagChangeEntity item : pending) {
            try {
                applyScheduledChange(item);
                item.setExecuted(true);
                item.setExecutedAt(Instant.now());
                scheduledChangeRepository.save(item);
                log.info("Executed scheduled change #{} for flag {}/{}", item.getId(), item.getProjectKey(), item.getFlagKey());
            } catch (Exception ex) {
                log.error("Failed to execute scheduled change #{}", item.getId(), ex);
            }
        }
    }

    private void applyScheduledChange(ScheduledFlagChangeEntity item) throws Exception {
        FlagEntity flag = flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(
                item.getProjectKey(), item.getEnvKey(), item.getFlagKey()
        ).orElseThrow(() -> new ResourceNotFoundException("Flag '" + item.getFlagKey() + "' not found"));

        Instant now = Instant.now();
        if ("KILL_SWITCH".equalsIgnoreCase(item.getAction())) {
            KillSwitchRequest req = objectMapper.readValue(item.getPayloadJson(), KillSwitchRequest.class);
            flag.setKillSwitch(req.killSwitch());
            if (req.killSwitch()) {
                flag.setFullRolloutSince(null);
            } else if (flag.isEnabled() && flag.getRolloutPercentage() >= 100.0) {
                if (flag.getFullRolloutSince() == null) {
                    flag.setFullRolloutSince(now);
                }
            }
        } else if ("UPDATE".equalsIgnoreCase(item.getAction()) || "SET_ROLLOUT".equalsIgnoreCase(item.getAction())) {
            UpdateFlagRequest req = objectMapper.readValue(item.getPayloadJson(), UpdateFlagRequest.class);
            if (req.rolloutPercentage() != null) flag.setRolloutPercentage(req.rolloutPercentage());
            if (req.rolloutVariationKey() != null) flag.setRolloutVariationKey(req.rolloutVariationKey());
            if (req.enabled() != null) flag.setEnabled(req.enabled());
            if (req.killSwitch() != null) flag.setKillSwitch(req.killSwitch());

            if (flag.isEnabled() && !flag.isKillSwitch() && flag.getRolloutPercentage() >= 100.0) {
                if (flag.getFullRolloutSince() == null) {
                    flag.setFullRolloutSince(now);
                }
            } else {
                flag.setFullRolloutSince(null);
            }
        }

        flag.setVersion(flag.getVersion() + 1);
        flag.setUpdatedAt(now);
        FlagEntity saved = flagRepository.save(flag);

        EnvironmentEntity env = projectService.requireEnvironmentEntity(item.getProjectKey(), item.getEnvKey());
        env.incrementRevision(now);
        environmentRepository.save(env);

        var response = jsonCodec.toFlagResponse(item.getProjectKey(), saved);

        FlagChangeHistoryEntity history = new FlagChangeHistoryEntity();
        history.setProjectKey(item.getProjectKey());
        history.setEnvKey(item.getEnvKey());
        history.setEntityType("FLAG");
        history.setEntityKey(item.getFlagKey());
        history.setVersion(saved.getVersion());
        history.setAuthor("Scheduler (requested by " + item.getAuthor() + ")");
        history.setReason(item.getReason());
        history.setAction("SCHEDULED_" + item.getAction());
        history.setCreatedAt(now);
        history.setDiffSummary("Scheduled change #" + item.getId() + " executed");
        historyRepository.save(history);

        sseBroadcasterService.broadcastFlagUpdate(item.getProjectKey(), item.getEnvKey(), "SCHEDULED_" + item.getAction(), response);
    }

    private ScheduledChangeResponse toResponse(ScheduledFlagChangeEntity e) {
        return new ScheduledChangeResponse(
                e.getId(),
                e.getProjectKey(),
                e.getEnvKey(),
                e.getFlagKey(),
                e.getAction(),
                e.getPayloadJson(),
                e.getAuthor(),
                e.getReason(),
                e.getScheduledTime(),
                e.isExecuted(),
                e.getExecutedAt(),
                e.getCreatedAt()
        );
    }
}
