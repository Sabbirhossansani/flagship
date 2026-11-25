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

    @GetMapping("/stale")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<FlagResponse> listStaleFlags(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return flagService.listFlags(projectKey, envKey, true, FlagshipPaging.of(page, size));
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

    @RequestMapping(value = "/{flagKey}/kill-switch", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public FlagResponse toggleKillSwitch(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @Valid @RequestBody(required = false) KillSwitchRequest req,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean killSwitch,
            @RequestParam(required = false) String reason
    ) {
        boolean resolvedKillSwitch;
        String resolvedReason;
        if (req != null) {
            resolvedKillSwitch = req.killSwitch();
            resolvedReason = (req.reason() != null && !req.reason().isBlank()) ? req.reason() : "Toggled from admin console";
        } else {
            resolvedKillSwitch = (killSwitch != null) ? killSwitch : Boolean.TRUE.equals(enabled);
            resolvedReason = (reason != null && !reason.isBlank()) ? reason : "Toggled from admin console";
        }
        return flagService.toggleKillSwitch(projectKey, envKey, flagKey, new KillSwitchRequest(resolvedKillSwitch, resolvedReason));
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
