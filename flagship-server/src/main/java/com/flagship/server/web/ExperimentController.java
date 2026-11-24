package com.flagship.server.web;

import com.flagship.server.dto.*;
import com.flagship.server.service.ExperimentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{envKey}")
public class ExperimentController {

    private final ExperimentService experimentService;

    public ExperimentController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public void recordEvents(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @Valid @RequestBody List<ExperimentEventDto> events
    ) {
        experimentService.recordEvents(projectKey, envKey, events);
    }

    @PostMapping("/experiments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectAccess.canOperate(authentication, #projectKey)")
    public ExperimentResponse createExperiment(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @Valid @RequestBody CreateExperimentRequest request
    ) {
        return experimentService.createExperiment(projectKey, envKey, request);
    }

    @GetMapping("/experiments")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<ExperimentResponse> listExperiments(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return experimentService.listExperiments(projectKey, envKey, FlagshipPaging.of(page, size));
    }

    @GetMapping("/flags/{flagKey}/experiment-results")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public ExperimentResultsResponse getExperimentResults(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String flagKey,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false) String baselineVariationKey
    ) {
        return experimentService.getExperimentResults(
                projectKey,
                envKey,
                flagKey,
                metricName,
                baselineVariationKey
        );
    }
}
