package com.flagship.server.security;

import com.flagship.core.model.ProjectRole;
import com.flagship.server.domain.ProjectRoleAssignmentEntity;
import com.flagship.server.domain.ProjectRoleAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Per-project RBAC evaluator used in {@code @PreAuthorize} expressions (FR10, C4).
 * Supports per-project role assignments stored in {@code project_role_assignments}
 * with fallback to global role authorities ({@code VIEWER}, {@code EDITOR}, {@code APPROVER}, {@code ADMIN}).
 */
@Component("projectAccess")
public class ProjectAccessEvaluator {

    private final ProjectRoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    public ProjectAccessEvaluator(
            @Autowired(required = false) ProjectRoleAssignmentRepository roleAssignmentRepository
    ) {
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    public boolean canView(Authentication authentication, String projectKey) {
        if (resolveRole(authentication, projectKey).isPresent()) {
            return true;
        }
        if ((projectKey == null || projectKey.isBlank())
                && authentication != null
                && authentication.isAuthenticated()
                && roleAssignmentRepository != null
                && authentication.getName() != null) {
            return roleAssignmentRepository.existsByUsername(authentication.getName());
        }
        return false;
    }

    public boolean canEdit(Authentication authentication, String projectKey) {
        return resolveRole(authentication, projectKey)
                .map(ProjectRole::canEdit)
                .orElse(false);
    }

    public boolean canOperate(Authentication authentication, String projectKey) {
        return resolveRole(authentication, projectKey)
                .map(role -> role.canEdit() || role.canApprove() || role.isAdmin())
                .orElse(false);
    }

    public boolean canApprove(Authentication authentication, String projectKey) {
        return resolveRole(authentication, projectKey)
                .map(ProjectRole::canApprove)
                .orElse(false);
    }

    public boolean isAdmin(Authentication authentication, String projectKey) {
        return resolveRole(authentication, projectKey)
                .map(ProjectRole::isAdmin)
                .orElse(false);
    }

    public Optional<ProjectRole> resolveRole(Authentication authentication, String projectKey) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String username = authentication.getName();
        if (projectKey != null && !projectKey.isBlank() && roleAssignmentRepository != null && username != null) {
            Optional<ProjectRoleAssignmentEntity> assigned =
                    roleAssignmentRepository.findByProjectKeyAndUsername(projectKey, username);
            if (assigned.isPresent()) {
                return Optional.of(assigned.get().getRole());
            }
        }
        return resolveGlobalRole(authentication);
    }

    public Optional<ProjectRole> resolveGlobalRole(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        ProjectRole highest = null;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority();
            ProjectRole candidate = switch (auth) {
                case "ROLE_ADMIN", "ADMIN" -> ProjectRole.ADMIN;
                case "ROLE_APPROVER", "APPROVER" -> ProjectRole.APPROVER;
                case "ROLE_EDITOR", "EDITOR" -> ProjectRole.EDITOR;
                case "ROLE_VIEWER", "VIEWER", "ROLE_SDK" -> ProjectRole.VIEWER;
                default -> null;
            };
            if (candidate != null && (highest == null || candidate.ordinal() > highest.ordinal())) {
                highest = candidate;
            }
        }
        return Optional.ofNullable(highest);
    }
}
