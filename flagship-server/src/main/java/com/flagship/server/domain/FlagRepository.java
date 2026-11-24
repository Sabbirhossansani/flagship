package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlagRepository extends JpaRepository<FlagEntity, Long> {

    Optional<FlagEntity> findByProject_ProjectKeyAndEnvKeyAndFlagKey(
            String projectKey,
            String envKey,
            String flagKey
    );

    boolean existsByProject_ProjectKeyAndEnvKeyAndFlagKey(
            String projectKey,
            String envKey,
            String flagKey
    );

    Page<FlagEntity> findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
            String projectKey,
            String envKey,
            Pageable pageable
    );

    List<FlagEntity> findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
            String projectKey,
            String envKey
    );

    List<FlagEntity> findAllByProject_ProjectKeyOrderByFlagKeyAsc(
            String projectKey
    );

    void deleteAllByProject_ProjectKeyAndEnvKey(String projectKey, String envKey);

    void deleteAllByProject_ProjectKey(String projectKey);
}
