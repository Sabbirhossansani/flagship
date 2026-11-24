package com.flagship.server.web;

import com.flagship.server.dto.StaleFlagDto;
import com.flagship.server.service.FlagHygieneService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}")
public class FlagHygieneController {

    private final FlagHygieneService flagHygieneService;

    public FlagHygieneController(FlagHygieneService flagHygieneService) {
        this.flagHygieneService = flagHygieneService;
    }

    @GetMapping("/environments/{envKey}/stale-flags")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public List<StaleFlagDto> getStaleFlags(
            @PathVariable String projectKey,
            @PathVariable String envKey
    ) {
        return flagHygieneService.findStaleFlags(projectKey, envKey);
    }

    @GetMapping("/stale-flags")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public List<StaleFlagDto> getProjectStaleFlags(
            @PathVariable String projectKey
    ) {
        return flagHygieneService.findStaleFlagsForProject(projectKey);
    }
}
