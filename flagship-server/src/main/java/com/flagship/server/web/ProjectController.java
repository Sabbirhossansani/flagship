package com.flagship.server.web;

import com.flagship.server.dto.*;
import com.flagship.server.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectAccess.canEdit(authentication, #req.key())")
    public ProjectResponse createProject(@Valid @RequestBody CreateProjectRequest req) {
        return projectService.createProject(req);
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canView(authentication, null)")
    public PageResponse<ProjectResponse> listProjects(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return projectService.listProjects(FlagshipPaging.of(page, size));
    }

    @GetMapping("/{projectKey}")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public ProjectResponse getProject(@PathVariable String projectKey) {
        return projectService.getProject(projectKey);
    }

    @PutMapping("/{projectKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public ProjectResponse updateProject(
            @PathVariable String projectKey,
            @Valid @RequestBody UpdateProjectRequest req
    ) {
        return projectService.updateProject(projectKey, req);
    }

    @DeleteMapping("/{projectKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectAccess.isAdmin(authentication, #projectKey)")
    public void deleteProject(@PathVariable String projectKey) {
        projectService.deleteProject(projectKey);
    }
}
