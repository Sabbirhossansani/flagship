package com.flagship.server.web;

import com.flagship.server.domain.ChangeRequestStatus;
import com.flagship.server.dto.*;
import com.flagship.server.service.ChangeControlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{envKey}")
public class ChangeControlController {

    private final ChangeControlService changeControlService;

    public ChangeControlController(ChangeControlService changeControlService) {
        this.changeControlService = changeControlService;
    }

    @GetMapping("/flags/{flagKey}/history")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<ChangeHistoryResponse> getFlagHistory(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return changeControlService.getFlagHistory(projectKey, envKey, flagKey, FlagshipPaging.of(page, size));
    }

    @PostMapping("/flags/{flagKey}/rollback")
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public FlagResponse rollbackFlag(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @Valid @RequestBody RollbackRequest request,
            Authentication authentication
    ) {
        String author = (authentication != null) ? authentication.getName() : "system";
        return changeControlService.rollbackFlag(
                projectKey,
                envKey,
                flagKey,
                request.targetVersion(),
                author,
                request.reason()
        );
    }

    @PostMapping("/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public ChangeRequestResponse createChangeRequest(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @Valid @RequestBody CreateChangeRequestDto dto,
            Authentication authentication
    ) {
        String author = (authentication != null) ? authentication.getName() : "system";
        return changeControlService.createChangeRequest(projectKey, envKey, dto, author);
    }

    @GetMapping("/change-requests")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<ChangeRequestResponse> listChangeRequests(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @RequestParam(required = false) ChangeRequestStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return changeControlService.listChangeRequests(projectKey, envKey, status, FlagshipPaging.of(page, size));
    }

    @PostMapping("/change-requests/{id}/approve")
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public ChangeRequestResponse approveChangeRequest(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable Long id,
            Authentication authentication
    ) {
        String approver = (authentication != null) ? authentication.getName() : "system";
        return changeControlService.approveChangeRequest(projectKey, envKey, id, approver);
    }

    @PostMapping("/change-requests/{id}/reject")
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public ChangeRequestResponse rejectChangeRequest(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable Long id,
            @RequestBody(required = false) RejectChangeRequest request,
            Authentication authentication
    ) {
        String rejector = (authentication != null) ? authentication.getName() : "system";
        String reason = (request != null && request.reason() != null) ? request.reason() : "Rejected by reviewer";
        return changeControlService.rejectChangeRequest(projectKey, envKey, id, rejector, reason);
    }
}
