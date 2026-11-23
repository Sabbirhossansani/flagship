package com.flagship.server.web;

import com.flagship.server.dto.*;
import com.flagship.server.service.FlagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{envKey}/flags")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public FlagResponse createFlag(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @Valid @RequestBody CreateFlagRequest req
    ) {
        return flagService.createFlag(projectKey, envKey, req);
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<FlagResponse> listFlags(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @RequestParam(required = false, defaultValue = "false") boolean staleOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return flagService.listFlags(projectKey, envKey, staleOnly, FlagshipPaging.of(page, size));
    }

    @GetMapping("/{flagKey}")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public FlagResponse getFlag(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey
    ) {
        return flagService.getFlag(projectKey, envKey, flagKey);
    }

    @PutMapping("/{flagKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public FlagResponse updateFlag(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @Valid @RequestBody UpdateFlagRequest req
    ) {
        return flagService.updateFlag(projectKey, envKey, flagKey, req);
    }

    @PatchMapping("/{flagKey}/kill-switch")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public FlagResponse toggleKillSwitch(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @Valid @RequestBody KillSwitchRequest req
    ) {
        return flagService.toggleKillSwitch(projectKey, envKey, flagKey, req);
    }

    @DeleteMapping("/{flagKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public void deleteFlag(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey
    ) {
        flagService.deleteFlag(projectKey, envKey, flagKey);
    }
}
