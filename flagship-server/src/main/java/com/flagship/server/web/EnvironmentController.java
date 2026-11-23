package com.flagship.server.web;

import com.flagship.server.dto.*;
import com.flagship.server.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments")
public class EnvironmentController {

    private final ProjectService projectService;

    public EnvironmentController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<EnvironmentResponse> listEnvironments(
            @PathVariable String projectKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return projectService.listEnvironments(projectKey, FlagshipPaging.of(page, size));
    }

    @GetMapping("/{envKey}")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public EnvironmentResponse getEnvironment(
            @PathVariable String projectKey,
            @PathVariable String envKey
    ) {
        return projectService.getEnvironment(projectKey, envKey);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectAccess.isAdmin(authentication, #projectKey)")
    public EnvironmentResponse createEnvironment(
            @PathVariable String projectKey,
            @Valid @RequestBody CreateEnvironmentRequest req
    ) {
        return projectService.createEnvironment(projectKey, req);
    }

    @PutMapping("/{envKey}")
    @PreAuthorize("@projectAccess.isAdmin(authentication, #projectKey)")
    public EnvironmentResponse updateEnvironment(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @Valid @RequestBody UpdateEnvironmentRequest req
    ) {
        return projectService.updateEnvironment(projectKey, envKey, req);
    }

    @DeleteMapping("/{envKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectAccess.isAdmin(authentication, #projectKey)")
    public void deleteEnvironment(
            @PathVariable String projectKey,
            @PathVariable String envKey
    ) {
        projectService.deleteEnvironment(projectKey, envKey);
    }
}
