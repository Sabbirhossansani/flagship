package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangeRequestRepository extends JpaRepository<ChangeRequestEntity, Long> {

    List<ChangeRequestEntity> findAllByProjectKeyAndEnvKeyOrderByCreatedAtDesc(String projectKey, String envKey);

    List<ChangeRequestEntity> findAllByProjectKeyAndEnvKeyAndStatusOrderByCreatedAtDesc(
            String projectKey,
            String envKey,
            ChangeRequestStatus status
    );

    Page<ChangeRequestEntity> findAllByProjectKeyAndEnvKeyOrderByCreatedAtDesc(
            String projectKey,
            String envKey,
            Pageable pageable
    );

    Page<ChangeRequestEntity> findAllByProjectKeyAndEnvKeyAndStatusOrderByCreatedAtDesc(
            String projectKey,
            String envKey,
            ChangeRequestStatus status,
            Pageable pageable
    );
}
