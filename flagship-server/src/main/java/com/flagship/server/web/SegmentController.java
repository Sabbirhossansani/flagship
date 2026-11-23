package com.flagship.server.web;

import com.flagship.server.dto.*;
import com.flagship.server.service.SegmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectKey}/environments/{envKey}/segments")
public class SegmentController {

    private final SegmentService segmentService;

    public SegmentController(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public SegmentResponse createSegment(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @Valid @RequestBody CreateSegmentRequest req
    ) {
        return segmentService.createSegment(projectKey, envKey, req);
    }

    @GetMapping
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public PageResponse<SegmentResponse> listSegments(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return segmentService.listSegments(projectKey, envKey, FlagshipPaging.of(page, size));
    }

    @GetMapping("/{segmentKey}")
    @PreAuthorize("@projectAccess.canView(authentication, #projectKey)")
    public SegmentResponse getSegment(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String segmentKey
    ) {
        return segmentService.getSegment(projectKey, envKey, segmentKey);
    }

    @PutMapping("/{segmentKey}")
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public SegmentResponse updateSegment(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String segmentKey,
            @Valid @RequestBody UpdateSegmentRequest req
    ) {
        return segmentService.updateSegment(projectKey, envKey, segmentKey, req);
    }

    @DeleteMapping("/{segmentKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectAccess.canEdit(authentication, #projectKey)")
    public void deleteSegment(
            @PathVariable String projectKey,
            @PathVariable String envKey,
            @PathVariable String segmentKey
    ) {
        segmentService.deleteSegment(projectKey, envKey, segmentKey);
    }
}
