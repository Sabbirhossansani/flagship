package com.flagship.server.web;

import com.flagship.server.dto.*;
import com.flagship.server.service.ProjectRbacService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/roles")
public class ProjectRoleController {

    private final ProjectRbacService projectRbacService;

    public ProjectRoleController(ProjectRbacService projectRbacService) {
        this.projectRbacService = projectRbacService;
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<RoleAssignmentResponse> listRoles(
            @PathVariable String projectKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return projectRbacService.listRoleAssignments(projectKey, FlagshipPaging.of(page, size));
    }

    @PutMapping("/{username}")
    @PreAuthorize("@projectAccess.isAdmin(authentication, #projectKey)")
    public RoleAssignmentResponse assignRole(
            @PathVariable String projectKey,
            @PathVariable String username,
            @Valid @RequestBody RoleAssignmentRequest req
    ) {
        return projectRbacService.assignRole(projectKey, username, req.role());
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectAccess.isAdmin(authentication, #projectKey)")
    public void removeRole(
            @PathVariable String projectKey,
            @PathVariable String username
    ) {
        projectRbacService.removeRoleAssignment(projectKey, username);
    }
}
