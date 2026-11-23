package com.flagship.server.service;

import com.flagship.core.model.EnvironmentDefinition;
import com.flagship.server.domain.EnvironmentEntity;
import com.flagship.server.domain.EnvironmentRepository;
import com.flagship.server.domain.FlagRepository;
import com.flagship.server.domain.ProjectEntity;
import com.flagship.server.domain.ProjectRepository;
import com.flagship.server.domain.ProjectRoleAssignmentRepository;
import com.flagship.server.domain.SegmentRepository;
import com.flagship.server.dto.CreateEnvironmentRequest;
import com.flagship.server.dto.CreateProjectRequest;
import com.flagship.server.dto.EnvironmentResponse;
import com.flagship.server.dto.PageResponse;
import com.flagship.server.dto.ProjectResponse;
import com.flagship.server.dto.UpdateEnvironmentRequest;
import com.flagship.server.dto.UpdateProjectRequest;
import com.flagship.server.exception.ResourceConflictException;
import com.flagship.server.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagRepository flagRepository;
    private final SegmentRepository segmentRepository;
    private final ProjectRoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    public ProjectService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            @Autowired(required = false) FlagRepository flagRepository,
            @Autowired(required = false) SegmentRepository segmentRepository,
            @Autowired(required = false) ProjectRoleAssignmentRepository roleAssignmentRepository
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.flagRepository = flagRepository;
        this.segmentRepository = segmentRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        if (projectRepository.existsByProjectKey(request.key())) {
            throw new ResourceConflictException("Project with key '" + request.key() + "' already exists");
        }
        Instant now = Instant.now();
        ProjectEntity project = new ProjectEntity(request.key(), request.name(), request.description(), now);

        List<CreateEnvironmentRequest> envRequests = request.environments();
        if (envRequests == null || envRequests.isEmpty()) {
            for (EnvironmentDefinition def : EnvironmentDefinition.defaultEnvironments()) {
                project.addEnvironment(new EnvironmentEntity(
                        project,
                        def.key(),
                        def.name(),
                        def.requiresApproval(),
                        now
                ));
            }
        } else {
            Set<String> seenKeys = new HashSet<>();
            for (CreateEnvironmentRequest envReq : envRequests) {
                if (!seenKeys.add(envReq.key())) {
                    throw new IllegalArgumentException("Duplicate environment key '" + envReq.key() + "' in project creation request");
                }
                project.addEnvironment(new EnvironmentEntity(
                        project,
                        envReq.key(),
                        envReq.name(),
                        envReq.requiresApproval(),
                        now
                ));
            }
        }

        ProjectEntity saved = projectRepository.save(project);
        return toProjectResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listProjects(Pageable pageable) {
        Page<ProjectEntity> page = projectRepository.findAllByOrderByProjectKeyAsc(pageable);
        return PageResponse.from(page, this::toProjectResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(String projectKey) {
        ProjectEntity project = requireProjectEntity(projectKey);
        return toProjectResponse(project);
    }

    @Transactional
    public ProjectResponse updateProject(String projectKey, UpdateProjectRequest request) {
        ProjectEntity project = requireProjectEntity(projectKey);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setUpdatedAt(Instant.now());
        ProjectEntity saved = projectRepository.save(project);
        return toProjectResponse(saved);
    }

    @Transactional
    public void deleteProject(String projectKey) {
        ProjectEntity project = requireProjectEntity(projectKey);
        if (flagRepository != null) {
            flagRepository.deleteAllByProject_ProjectKey(projectKey);
        }
        if (segmentRepository != null) {
            segmentRepository.deleteAllByProject_ProjectKey(projectKey);
        }
        if (roleAssignmentRepository != null) {
            roleAssignmentRepository.deleteAllByProjectKey(projectKey);
        }
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<EnvironmentResponse> listEnvironments(String projectKey, Pageable pageable) {
        requireProjectEntity(projectKey);
        Page<EnvironmentEntity> page = environmentRepository.findAllByProject_ProjectKeyOrderByIdAsc(projectKey, pageable);
        return PageResponse.from(page, this::toEnvironmentResponse);
    }

    @Transactional(readOnly = true)
    public EnvironmentResponse getEnvironment(String projectKey, String envKey) {
        EnvironmentEntity env = requireEnvironmentEntity(projectKey, envKey);
        return toEnvironmentResponse(env);
    }

    @Transactional
    public EnvironmentResponse createEnvironment(String projectKey, CreateEnvironmentRequest request) {
        ProjectEntity project = requireProjectEntity(projectKey);
        if (environmentRepository.existsByProject_ProjectKeyAndEnvKey(projectKey, request.key())) {
            throw new ResourceConflictException(
                    "Environment '" + request.key() + "' already exists in project '" + projectKey + "'"
            );
        }
        Instant now = Instant.now();
        EnvironmentEntity env = new EnvironmentEntity(
                project,
                request.key(),
                request.name(),
                request.requiresApproval(),
                now
        );
        EnvironmentEntity saved = environmentRepository.save(env);
        return toEnvironmentResponse(saved);
    }

    @Transactional
    public EnvironmentResponse updateEnvironment(String projectKey, String envKey, UpdateEnvironmentRequest request) {
        EnvironmentEntity env = requireEnvironmentEntity(projectKey, envKey);
        env.setName(request.name());
        env.setRequiresApproval(request.requiresApproval());
        env.setUpdatedAt(Instant.now());
        EnvironmentEntity saved = environmentRepository.save(env);
        return toEnvironmentResponse(saved);
    }

    @Transactional
    public void deleteEnvironment(String projectKey, String envKey) {
        EnvironmentEntity env = requireEnvironmentEntity(projectKey, envKey);
        if (flagRepository != null) {
            flagRepository.deleteAllByProject_ProjectKeyAndEnvKey(projectKey, envKey);
        }
        if (segmentRepository != null) {
            segmentRepository.deleteAllByProject_ProjectKeyAndEnvKey(projectKey, envKey);
        }
        ProjectEntity project = env.getProject();
        if (project != null) {
            project.getEnvironments().remove(env);
        }
        environmentRepository.delete(env);
    }

    public ProjectEntity requireProjectEntity(String projectKey) {
        return projectRepository.findByProjectKey(projectKey)
                .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectKey + "' not found"));
    }

    public EnvironmentEntity requireEnvironmentEntity(String projectKey, String envKey) {
        return environmentRepository.findByProject_ProjectKeyAndEnvKey(projectKey, envKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Environment '" + envKey + "' not found in project '" + projectKey + "'"
                ));
    }

    public ProjectResponse toProjectResponse(ProjectEntity entity) {
        List<EnvironmentResponse> envs = entity.getEnvironments().stream()
                .map(this::toEnvironmentResponse)
                .toList();
        return new ProjectResponse(
                entity.getProjectKey(),
                entity.getName(),
                entity.getDescription(),
                envs,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public EnvironmentResponse toEnvironmentResponse(EnvironmentEntity entity) {
        return new EnvironmentResponse(
                entity.getEnvKey(),
                entity.getName(),
                entity.isRequiresApproval(),
                entity.getRevision(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
