package com.flagship.server.service;

import com.flagship.core.model.ProjectRole;
import com.flagship.server.domain.ProjectRoleAssignmentEntity;
import com.flagship.server.domain.ProjectRoleAssignmentRepository;
import com.flagship.server.dto.PageResponse;
import com.flagship.server.dto.RoleAssignmentResponse;
import com.flagship.server.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class ProjectRbacService {

    private final ProjectService projectService;
    private final ProjectRoleAssignmentRepository roleAssignmentRepository;

    public ProjectRbacService(
            ProjectService projectService,
            ProjectRoleAssignmentRepository roleAssignmentRepository
    ) {
        this.projectService = projectService;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional
    public RoleAssignmentResponse assignRole(String projectKey, String username, ProjectRole role) {
        projectService.requireProjectEntity(projectKey);
        Instant now = Instant.now();
        ProjectRoleAssignmentEntity entity = roleAssignmentRepository
                .findByProjectKeyAndUsername(projectKey, username)
                .map(existing -> {
                    existing.setRole(role);
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> new ProjectRoleAssignmentEntity(projectKey, username, role, now));

        ProjectRoleAssignmentEntity saved = roleAssignmentRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleAssignmentResponse> listRoleAssignments(String projectKey, Pageable pageable) {
        projectService.requireProjectEntity(projectKey);
        Page<ProjectRoleAssignmentEntity> page = roleAssignmentRepository
                .findAllByProjectKeyOrderByUsernameAsc(projectKey, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional
    public void removeRoleAssignment(String projectKey, String username) {
        projectService.requireProjectEntity(projectKey);
        ProjectRoleAssignmentEntity entity = roleAssignmentRepository
                .findByProjectKeyAndUsername(projectKey, username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role assignment for user '" + username + "' in project '" + projectKey + "' not found"
                ));
        roleAssignmentRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectRole> findUserRoleForProject(String projectKey, String username) {
        return roleAssignmentRepository.findByProjectKeyAndUsername(projectKey, username)
                .map(ProjectRoleAssignmentEntity::getRole);
    }

    private RoleAssignmentResponse toResponse(ProjectRoleAssignmentEntity entity) {
        return new RoleAssignmentResponse(
                entity.getProjectKey(),
                entity.getUsername(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
