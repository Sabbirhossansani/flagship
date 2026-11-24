package com.flagship.server.web;

import com.flagship.server.dto.ScheduleFlagChangeRequest;
import com.flagship.server.dto.ScheduledChangeResponse;
import com.flagship.server.service.ScheduledFlagChangeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{envKey}/flags/{flagKey}/schedules")
public class ScheduledChangeController {

    private final ScheduledFlagChangeService scheduledChangeService;

    public ScheduledChangeController(ScheduledFlagChangeService scheduledChangeService) {
        this.scheduledChangeService = scheduledChangeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public ScheduledChangeResponse scheduleChange(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @Valid @RequestBody ScheduleFlagChangeRequest request,
            Authentication authentication
    ) {
        String author = (authentication != null) ? authentication.getName() : "system";
        return scheduledChangeService.scheduleChange(projectKey, envKey, flagKey, request, author);
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public List<ScheduledChangeResponse> listSchedules(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey
    ) {
        return scheduledChangeService.listSchedules(projectKey, envKey, flagKey);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public void cancelSchedule(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @PathVariable Long id
    ) {
        scheduledChangeService.cancelSchedule(projectKey, envKey, flagKey, id);
    }
}
