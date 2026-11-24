package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlagChangeHistoryRepository extends JpaRepository<FlagChangeHistoryEntity, Long> {

    List<FlagChangeHistoryEntity> findAllByProjectKeyAndEnvKeyAndEntityTypeAndEntityKeyOrderByVersionDesc(
            String projectKey,
            String envKey,
            String entityType,
            String entityKey
    );

    Page<FlagChangeHistoryEntity> findAllByProjectKeyAndEnvKeyAndEntityTypeAndEntityKeyOrderByVersionDesc(
            String projectKey,
            String envKey,
            String entityType,
            String entityKey,
            Pageable pageable
    );

    Optional<FlagChangeHistoryEntity> findByProjectKeyAndEnvKeyAndEntityTypeAndEntityKeyAndVersion(
            String projectKey,
            String envKey,
            String entityType,
            String entityKey,
            long version
    );
}
