package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, Long> {

    Optional<EnvironmentEntity> findByProject_ProjectKeyAndEnvKey(String projectKey, String envKey);

    boolean existsByProject_ProjectKeyAndEnvKey(String projectKey, String envKey);

    Page<EnvironmentEntity> findAllByProject_ProjectKeyOrderByIdAsc(String projectKey, Pageable pageable);

    List<EnvironmentEntity> findAllByProject_ProjectKeyOrderByIdAsc(String projectKey);
}
