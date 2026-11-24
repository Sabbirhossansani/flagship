package com.flagship.server.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScheduledFlagChangeRepository extends JpaRepository<ScheduledFlagChangeEntity, Long> {

    List<ScheduledFlagChangeEntity> findAllByExecutedFalseAndScheduledTimeLessThanEqualOrderByScheduledTimeAsc(
            Instant cutoff
    );

    List<ScheduledFlagChangeEntity> findAllByProjectKeyAndEnvKeyAndFlagKeyOrderByScheduledTimeAsc(
            String projectKey,
            String envKey,
            String flagKey
    );
}
