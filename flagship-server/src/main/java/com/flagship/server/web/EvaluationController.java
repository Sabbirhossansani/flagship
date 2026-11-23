package com.flagship.server.web;

import com.flagship.core.model.EvaluationResult;
import com.flagship.server.dto.EnvironmentSnapshotResponse;
import com.flagship.server.dto.EvaluationContextRequest;
import com.flagship.server.service.EvaluationPreviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{envKey}")
public class EvaluationController {

    private final EvaluationPreviewService evaluationPreviewService;

    public EvaluationController(EvaluationPreviewService evaluationPreviewService) {
        this.evaluationPreviewService = evaluationPreviewService;
    }

    @GetMapping("/snapshot")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public EnvironmentSnapshotResponse getSnapshot(
            @PathVariable String projectKey,
            @PathVariable String envKey
    ) {
        return evaluationPreviewService.getEnvironmentSnapshot(projectKey, envKey);
    }

    @PostMapping("/flags/{flagKey}/evaluate")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public EvaluationResult evaluateFlag(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @RequestBody(required = false) EvaluationContextRequest req
    ) {
        return evaluationPreviewService.evaluateFlag(projectKey, envKey, flagKey, req);
    }

    @PostMapping("/evaluate-all")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public Map<String, EvaluationResult> evaluateAllFlags(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @RequestBody(required = false) EvaluationContextRequest req
    ) {
        return evaluationPreviewService.evaluateAllFlags(projectKey, envKey, req);
    }
}
