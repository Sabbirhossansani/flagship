package com.flagship.server.web;

import com.flagship.server.service.ProjectService;
import com.flagship.server.service.SseBroadcasterService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{envKey}/stream")
public class SseStreamController {

    private final SseBroadcasterService sseBroadcasterService;
    private final ProjectService projectService;

    public SseStreamController(
            SseBroadcasterService sseBroadcasterService,
            ProjectService projectService
    ) {
        this.sseBroadcasterService = sseBroadcasterService;
        this.projectService = projectService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public SseEmitter subscribe(
            @PathVariable String projectKey,
            @PathVariable String envKey
    ) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        return sseBroadcasterService.subscribe(projectKey, envKey);
    }
}
