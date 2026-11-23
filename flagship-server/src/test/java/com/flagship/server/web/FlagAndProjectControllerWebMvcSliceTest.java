package com.flagship.server.web;

import com.flagship.core.model.ProjectRole;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.server.config.TraceIdFilter;
import com.flagship.server.domain.ProjectRoleAssignmentEntity;
import com.flagship.server.domain.ProjectRoleAssignmentRepository;
import com.flagship.server.dto.FlagResponse;
import com.flagship.server.dto.PageResponse;
import com.flagship.server.dto.ProjectResponse;
import com.flagship.server.exception.GlobalExceptionHandler;
import com.flagship.server.exception.ResourceConflictException;
import com.flagship.server.exception.ResourceNotFoundException;
import com.flagship.server.exception.RuleValidationException;
import com.flagship.server.security.JwtAndApiKeyAuthenticationFilter;
import com.flagship.server.security.JwtTokenService;
import com.flagship.server.security.ProjectAccessEvaluator;
import com.flagship.server.security.SecurityConfig;
import com.flagship.server.service.FlagService;
import com.flagship.server.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice test verifying REST controllers, Spring Security RBAC, Bean Validation,
 * Trace ID propagation, and RFC 9457 {@code ProblemDetail} responses (C4, C5).
 */
@WebMvcTest(controllers = {ProjectController.class, FlagController.class})
@Import({
        SecurityConfig.class,
        ProjectAccessEvaluator.class,
        JwtAndApiKeyAuthenticationFilter.class,
        JwtTokenService.class,
        GlobalExceptionHandler.class,
        TraceIdFilter.class
})
@ActiveProfiles("test")
class FlagAndProjectControllerWebMvcSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private FlagService flagService;

    @MockitoBean
    private ProjectRoleAssignmentRepository roleAssignmentRepository;

    @Test
    void unauthenticatedRequestReturns401ProblemDetailWithTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("X-Trace-Id", "trace-slice-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", "trace-slice-401"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.traceId").value("trace-slice-401"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerCanListProjectsButCannotCreateOrDeleteProject() throws Exception {
        ProjectResponse sample = new ProjectResponse(
                "payments", "Payments", "Payments service", List.of(), Instant.now(), Instant.now()
        );
        when(projectService.listProjects(any()))
                .thenReturn(new PageResponse<>(List.of(sample), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].key").value("payments"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"payments\",\"name\":\"Payments\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        mockMvc.perform(delete("/api/v1/projects/payments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void editorCanCreateProjectButCannotDeleteProjectAndValidationReturns400ProblemDetail() throws Exception {
        ProjectResponse sample = new ProjectResponse(
                "billing", "Billing", "Billing service", List.of(), Instant.now(), Instant.now()
        );
        when(projectService.createProject(any())).thenReturn(sample);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"billing\",\"name\":\"Billing\",\"description\":\"Billing service\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("billing"));

        // Invalid project key triggers Bean Validation 400 ProblemDetail
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"INVALID KEY!\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.key").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        // Editor cannot delete project (ADMIN required)
        mockMvc.perform(delete("/api/v1/projects/billing"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void mapsNotFoundConflictAndRuleValidationExceptionsToRfc9457ProblemDetail() throws Exception {
        when(projectService.getProject("unknown"))
                .thenThrow(new ResourceNotFoundException("Project 'unknown' not found"));
        when(projectService.createProject(any()))
                .thenThrow(new ResourceConflictException("Project with key 'dup' already exists"));
        when(flagService.createFlag(eq("payments"), eq("production"), any()))
                .thenThrow(new RuleValidationException(
                        "Flag targeting rules failed validation",
                        List.of("Invalid regular expression '[unclosed' for operator REGEX")
                ));

        mockMvc.perform(get("/api/v1/projects/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"dup\",\"name\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Resource Conflict"));

        mockMvc.perform(post("/api/v1/projects/payments/environments/production/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "regex-flag",
                                  "name": "Regex Flag",
                                  "type": "BOOLEAN",
                                  "variations": [{"key":"on","type":"BOOLEAN","value":true}],
                                  "defaultVariationKey": "on"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Rule Tree Validation Failed"))
                .andExpect(jsonPath("$.errors[0]").value("Invalid regular expression '[unclosed' for operator REGEX"));
    }

    @Test
    @WithMockUser(username = "scoped-user", roles = "VIEWER")
    void perProjectRbacAssignmentGrantsEditorOnAssignedProjectOnly() throws Exception {
        when(roleAssignmentRepository.findByProjectKeyAndUsername("alpha", "scoped-user"))
                .thenReturn(Optional.of(new ProjectRoleAssignmentEntity(
                        "alpha", "scoped-user", ProjectRole.EDITOR, Instant.now()
                )));
        when(roleAssignmentRepository.findByProjectKeyAndUsername("beta", "scoped-user"))
                .thenReturn(Optional.empty());

        FlagResponse sampleFlag = new FlagResponse(
                "alpha", "development", "new-ui", "New UI", null, "ui-team",
                VariationType.BOOLEAN, true, false,
                List.of(Variation.ofBoolean("on", true), Variation.ofBoolean("off", false)),
                "on", "off", Map.of(), List.of(), 0.0, null, List.of(),
                1L, false, Instant.now(), Instant.now(), null, null
        );
        when(flagService.createFlag(eq("alpha"), eq("development"), any())).thenReturn(sampleFlag);

        String payload = """
                {
                  "key": "new-ui",
                  "name": "New UI",
                  "type": "BOOLEAN",
                  "variations": [
                    {"key":"on","type":"BOOLEAN","value":true},
                    {"key":"off","type":"BOOLEAN","value":false}
                  ],
                  "defaultVariationKey": "on",
                  "offVariationKey": "off"
                }
                """;

        // Allowed on project 'alpha' where scoped-user has EDITOR assignment
        mockMvc.perform(post("/api/v1/projects/alpha/environments/development/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("new-ui"));

        // Denied on project 'beta' where scoped-user only has global VIEWER
        mockMvc.perform(post("/api/v1/projects/beta/environments/development/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void handlesTypeMismatchMethodNotAllowedAndKillSwitchValidationAsRfc9457ProblemDetails() throws Exception {
        // Query param type mismatch -> 400 Bad Request ProblemDetail (not 500)
        mockMvc.perform(get("/api/v1/projects?page=not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Parameter Type"));

        // Unsupported HTTP method -> 405 Method Not Allowed ProblemDetail (not 500)
        mockMvc.perform(patch("/api/v1/projects"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));

        // Kill switch request missing 'killSwitch' boolean field -> 400 Validation Error ProblemDetail
        mockMvc.perform(patch("/api/v1/projects/alpha/environments/development/flags/new-ui/kill-switch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"missing killSwitch field\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.killSwitch").exists());
    }
}
