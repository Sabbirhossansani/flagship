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

    @GetMapping({"/audit", "/change-control/audit"})
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<ChangeHistoryResponse> getAuditHistory(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return changeControlService.getAuditHistory(projectKey, envKey, FlagshipPaging.of(page, size));
    }

    @PostMapping({"/flags/{flagKey}/rollback", "/change-control/flags/{flagKey}/rollback"})
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public FlagResponse rollbackFlag(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @Valid @RequestBody(required = false) RollbackRequest request,
            @RequestParam(required = false) Long targetVersion,
            @RequestParam(required = false) String reason,
            Authentication authentication
    ) {
        String author = (authentication != null) ? authentication.getName() : "system";
        long resolvedVersion = (request != null && request.targetVersion() != null)
                ? request.targetVersion()
                : (targetVersion != null ? targetVersion : 1L);
        String resolvedReason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : (reason != null && !reason.isBlank() ? reason : "Rollback to version " + resolvedVersion);

        return changeControlService.rollbackFlag(
                projectKey,
                envKey,
                flagKey,
                resolvedVersion,
                author,
                resolvedReason
        );
    }

    @PostMapping({"/change-requests", "/change-control/requests"})
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

    @GetMapping({"/change-requests", "/change-control/requests"})
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

    @PostMapping({"/change-requests/{id}/approve", "/change-control/requests/{id}/approve"})
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public ChangeRequestResponse approveChangeRequest(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable Long id,
            @RequestParam(required = false) String approver,
            Authentication authentication
    ) {
        String resolvedApprover = (approver != null && !approver.isBlank())
                ? approver
                : ((authentication != null) ? authentication.getName() : "system");
        return changeControlService.approveChangeRequest(projectKey, envKey, id, resolvedApprover);
    }

    @PostMapping({"/change-requests/{id}/reject", "/change-control/requests/{id}/reject"})
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public ChangeRequestResponse rejectChangeRequest(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable Long id,
            @RequestBody(required = false) RejectChangeRequest request,
            @RequestParam(required = false) String reason,
            Authentication authentication
    ) {
        String rejector = (authentication != null) ? authentication.getName() : "system";
        String resolvedReason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : (reason != null && !reason.isBlank() ? reason : "Rejected by reviewer");
        return changeControlService.rejectChangeRequest(projectKey, envKey, id, rejector, resolvedReason);
    }
}
