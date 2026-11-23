package com.flagship.server.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRoleAssignmentRepository extends JpaRepository<ProjectRoleAssignmentEntity, Long> {

    Optional<ProjectRoleAssignmentEntity> findByProjectKeyAndUsername(String projectKey, String username);

    boolean existsByUsername(String username);

    Page<ProjectRoleAssignmentEntity> findAllByProjectKeyOrderByUsernameAsc(String projectKey, Pageable pageable);

    void deleteAllByProjectKey(String projectKey);
}
