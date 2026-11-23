package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SegmentRepository extends JpaRepository<SegmentEntity, Long> {

    Optional<SegmentEntity> findByProject_ProjectKeyAndEnvKeyAndSegmentKey(
            String projectKey,
            String envKey,
            String segmentKey
    );

    boolean existsByProject_ProjectKeyAndEnvKeyAndSegmentKey(
            String projectKey,
            String envKey,
            String segmentKey
    );

    Page<SegmentEntity> findAllByProject_ProjectKeyAndEnvKeyOrderBySegmentKeyAsc(
            String projectKey,
            String envKey,
            Pageable pageable
    );

    List<SegmentEntity> findAllByProject_ProjectKeyAndEnvKeyOrderBySegmentKeyAsc(
            String projectKey,
            String envKey
    );

    void deleteAllByProject_ProjectKeyAndEnvKey(String projectKey, String envKey);

    void deleteAllByProject_ProjectKey(String projectKey);
}
