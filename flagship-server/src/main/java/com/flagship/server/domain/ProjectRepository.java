package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    Optional<ProjectEntity> findByProjectKey(String projectKey);

    boolean existsByProjectKey(String projectKey);

    Page<ProjectEntity> findAllByOrderByProjectKeyAsc(Pageable pageable);
}
