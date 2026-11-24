package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperimentRepository extends JpaRepository<ExperimentEntity, Long> {

    List<ExperimentEntity> findAllByProjectKeyAndEnvKeyOrderByCreatedAtDesc(String projectKey, String envKey);

    Page<ExperimentEntity> findAllByProjectKeyAndEnvKeyOrderByCreatedAtDesc(
            String projectKey,
            String envKey,
            Pageable pageable
    );

    Optional<ExperimentEntity> findByProjectKeyAndEnvKeyAndExperimentKey(
            String projectKey,
            String envKey,
            String experimentKey
    );

    Optional<ExperimentEntity> findByProjectKeyAndEnvKeyAndFlagKey(
            String projectKey,
            String envKey,
            String flagKey
    );
}
