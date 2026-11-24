package com.flagship.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full end-to-end integration test for FlagShip Server Phase 2:
 * CRUD (Projects, Environments, Segments, Flags), Flyway schema, Spring Security JWT + API Key + per-project RBAC,
 * OpenAPI, Actuator, RFC 9457 ProblemDetail, and evaluation preview/snapshot endpoints (FR1-FR3, FR10, C1-C5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FlagshipServerIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabaseBeforeEach() {
        TestDatabaseCleaner.cleanAllTables(jdbcTemplate);
    }

    @AfterEach
    void cleanDatabaseAfterEach() {
        TestDatabaseCleaner.cleanAllTables(jdbcTemplate);
    }

    @Test
    void fullControlPlaneLifecycleWithJwtRbacEvaluationPreviewAndSnapshot() throws Exception {
        // 1. Issue JWT tokens for admin and viewer
        String adminToken = obtainJwtToken("admin", "admin-test-password");
        String viewerToken = obtainJwtToken("viewer", "viewer-test-password");

        // Verify /api/v1/auth/me works with Bearer JWT
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));

        // Invalid login returns 401 RFC 9457 ProblemDetail
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        // 2. Create project 'fintech' (automatically creates development, staging, production environments)
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "fintech",
                                  "name": "FinTech Core",
                                  "description": "Core payment & checkout flags"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("fintech"))
                .andExpect(jsonPath("$.environments.length()").value(3))
                .andExpect(jsonPath("$.environments[2].key").value("production"))
                .andExpect(jsonPath("$.environments[2].requiresApproval").value(true));

        // Duplicate project key returns 409 Conflict ProblemDetail
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"fintech\",\"name\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Paginated list of projects & environments
        mockMvc.perform(get("/api/v1/projects?page=0&size=10")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].key").value("fintech"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/projects/fintech/environments?page=0&size=10")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        // 3. Create reusable Segment 'bd-mobile-v2' with Composite + Visitor rule tree
        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/segments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "bd-mobile-v2",
                                  "name": "Bangladesh Mobile v2+ Users",
                                  "description": "Users in BD on appVersion > 2.0.0",
                                  "condition": {
                                    "type": "COMPOSITE",
                                    "logicalOperator": "AND",
                                    "children": [
                                      {
                                        "type": "LEAF",
                                        "attribute": "country",
                                        "operator": "IN",
                                        "values": ["BD"]
                                      },
                                      {
                                        "type": "LEAF",
                                        "attribute": "appVersion",
                                        "operator": "SEMVER_GT",
                                        "values": ["2.0.0"]
                                      }
                                    ]
                                  },
                                  "includedUsers": ["always-in-user"],
                                  "excludedUsers": ["blocked-bd-user"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("bd-mobile-v2"))
                .andExpect(jsonPath("$.conditionDescription").value("((country IN [BD]) AND (appVersion SEMVER_GT [2.0.0]))"));

        // Invalid semver in Segment rule tree returns 400 ProblemDetail from RuleValidationVisitor
        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/segments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "broken-segment",
                                  "name": "Broken Segment",
                                  "condition": {
                                    "type": "LEAF",
                                    "attribute": "appVersion",
                                    "operator": "SEMVER_GT",
                                    "values": ["not-a-semver"]
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Rule Tree Validation Failed"))
                .andExpect(jsonPath("$.errors[0]").exists());

        // 4. Create Multi-Type Feature Flags (STRING & BOOLEAN) with targets, rules, and rollouts
        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "payment-gateway",
                                  "name": "Payment Gateway Routing",
                                  "description": "Selects regional payment gateway",
                                  "owner": "payments-team",
                                  "type": "STRING",
                                  "enabled": true,
                                  "killSwitch": false,
                                  "variations": [
                                    {"key": "bkash", "type": "STRING", "value": "bkash-v3"},
                                    {"key": "stripe", "type": "STRING", "value": "stripe-global"},
                                    {"key": "legacy", "type": "STRING", "value": "legacy-bank"}
                                  ],
                                  "defaultVariationKey": "stripe",
                                  "offVariationKey": "legacy",
                                  "individualTargets": {
                                    "qa-tester-1": "bkash"
                                  },
                                  "rules": [
                                    {
                                      "id": "rule-bd-mobile",
                                      "name": "BD Mobile V2 Rule",
                                      "segmentKey": "bd-mobile-v2",
                                      "variationKey": "bkash"
                                    }
                                  ],
                                  "rolloutPercentage": 0.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("payment-gateway"))
                .andExpect(jsonPath("$.version").value(1));

        // 5. Evaluate flag via Server Preview API (verifying Chain of Responsibility order)
        // 5a. Individual user target match -> INDIVIDUAL_TARGET
        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/flags/payment-gateway/evaluate")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userKey": "qa-tester-1",
                                  "attributes": {"country": "US"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variationKey").value("bkash"))
                .andExpect(jsonPath("$.value").value("bkash-v3"))
                .andExpect(jsonPath("$.reason").value("INDIVIDUAL_TARGET"));

        // 5b. Segment rule match -> SEGMENT_RULE
        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/flags/payment-gateway/evaluate")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userKey": "dhaka-user-42",
                                  "attributes": {"country": "BD", "appVersion": "2.4.1"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variationKey").value("bkash"))
                .andExpect(jsonPath("$.reason").value("SEGMENT_RULE"))
                .andExpect(jsonPath("$.matchedRuleId").value("rule-bd-mobile"));

        // 5c. Excluded user in segment -> falls through to DEFAULT_VARIATION ('stripe')
        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/flags/payment-gateway/evaluate")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userKey": "blocked-bd-user",
                                  "attributes": {"country": "BD", "appVersion": "2.4.1"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variationKey").value("stripe"))
                .andExpect(jsonPath("$.reason").value("DEFAULT_VARIATION"));

        // 5d. Toggle Kill Switch ON -> immediately overrides individual targets and rules with KILL_SWITCH ('legacy')
        mockMvc.perform(patch("/api/v1/projects/fintech/environments/production/flags/payment-gateway/kill-switch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"killSwitch\":true,\"reason\":\"Emergency gateway maintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.killSwitch").value(true))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/flags/payment-gateway/evaluate")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"qa-tester-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variationKey").value("legacy"))
                .andExpect(jsonPath("$.value").value("legacy-bank"))
                .andExpect(jsonPath("$.reason").value("KILL_SWITCH"));

        // Turn kill switch back off and evaluate all flags
        mockMvc.perform(patch("/api/v1/projects/fintech/environments/production/flags/payment-gateway/kill-switch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"killSwitch\":false,\"reason\":\"Maintenance complete\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.killSwitch").value(false));

        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/evaluate-all")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"qa-tester-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['payment-gateway'].variationKey").value("bkash"));

        // 6. SDK Environment Snapshot endpoint authenticated via X-API-Key header
        mockMvc.perform(get("/api/v1/projects/fintech/environments/production/snapshot")
                        .header("X-API-Key", "fs_test_bootstrap_key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("fintech"))
                .andExpect(jsonPath("$.environmentKey").value("production"))
                .andExpect(jsonPath("$.revision").value(5))
                .andExpect(jsonPath("$.flags.['payment-gateway'].key").value("payment-gateway"))
                .andExpect(jsonPath("$.segments.['bd-mobile-v2'].key").value("bd-mobile-v2"));

        // 7. Per-project RBAC: viewer is denied flag creation initially, then granted EDITOR on 'fintech'
        String createBoolFlagPayload = """
                {
                  "key": "instant-refund",
                  "name": "Instant Refund",
                  "type": "BOOLEAN",
                  "variations": [
                    {"key": "on", "type": "BOOLEAN", "value": true},
                    {"key": "off", "type": "BOOLEAN", "value": false}
                  ],
                  "defaultVariationKey": "off"
                }
                """;

        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/flags")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBoolFlagPayload))
                .andExpect(status().isForbidden());

        // Admin grants EDITOR role on project 'fintech' to user 'viewer'
        mockMvc.perform(put("/api/v1/projects/fintech/roles/viewer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("fintech"))
                .andExpect(jsonPath("$.username").value("viewer"))
                .andExpect(jsonPath("$.role").value("EDITOR"));

        // Now 'viewer' can create flags in 'fintech'!
        mockMvc.perform(post("/api/v1/projects/fintech/environments/production/flags")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBoolFlagPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("instant-refund"));

        // 8. Verify OpenAPI /v3/api-docs and Actuator /actuator/health endpoints
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FlagShip Control Plane API"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.status").value("UP"));

        // Clean up project at the end of test (verifying cascading cleanup of project_role_assignments)
        mockMvc.perform(delete("/api/v1/projects/fintech")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void crudUpdatesDeletesMultiTypesBasicAuthAndEdgeCases() throws Exception {
        String adminToken = obtainJwtToken("admin", "admin-test-password");

        // Create project with custom environments
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "catalog",
                                  "name": "Catalog Service",
                                  "description": "Initial description",
                                  "environments": [
                                    {"key": "dev", "name": "Dev", "requiresApproval": false},
                                    {"key": "stage", "name": "Stage", "requiresApproval": false}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.environments.length()").value(2));

        // Update project
        mockMvc.perform(put("/api/v1/projects/catalog")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Catalog V2\",\"description\":\"Updated description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Catalog V2"));

        // Create, update, populate with a flag, and delete an environment (verifying no orphaned flags remain)
        mockMvc.perform(post("/api/v1/projects/catalog/environments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"sandbox\",\"name\":\"Sandbox\",\"requiresApproval\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("sandbox"));

        mockMvc.perform(post("/api/v1/projects/catalog/environments/sandbox/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "temp-sandbox-flag",
                                  "name": "Temp Sandbox Flag",
                                  "type": "BOOLEAN",
                                  "variations": [{"key": "on", "value": true}],
                                  "defaultVariationKey": "on"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variations[0].type").value("BOOLEAN"));

        mockMvc.perform(put("/api/v1/projects/catalog/environments/sandbox")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sandbox Strict\",\"requiresApproval\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresApproval").value(true));

        mockMvc.perform(delete("/api/v1/projects/catalog/environments/sandbox")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Recreating 'sandbox' environment should have 0 flags (no orphans from deleted environment)
        mockMvc.perform(post("/api/v1/projects/catalog/environments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"sandbox\",\"name\":\"Sandbox Recreated\",\"requiresApproval\":false}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/projects/catalog/environments/sandbox/flags")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(delete("/api/v1/projects/catalog/environments/sandbox")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Create, update, list, and delete a Segment
        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/segments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "staff-users",
                                  "name": "Staff Users",
                                  "condition": {
                                    "type": "LEAF",
                                    "attribute": "email",
                                    "operator": "CONTAINS",
                                    "values": ["@flagship.dev"]
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/v1/projects/catalog/environments/dev/segments/staff-users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Staff & QA Users",
                                  "description": "Internal domains",
                                  "condition": {
                                    "type": "LEAF",
                                    "attribute": "email",
                                    "operator": "REGEX",
                                    "values": ["^.+@(flagship\\\\.dev|qa\\\\.local)$"]
                                  },
                                  "includedUsers": ["founder-1"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.includedUsers[0]").value("founder-1"));

        mockMvc.perform(get("/api/v1/projects/catalog/environments/dev/segments/staff-users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Staff & QA Users"));

        // Create NUMBER and JSON flags
        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "max-cart-items",
                                  "name": "Max Cart Items",
                                  "type": "NUMBER",
                                  "variations": [
                                    {"key": "standard", "type": "NUMBER", "value": 25},
                                    {"key": "staff", "type": "NUMBER", "value": 100}
                                  ],
                                  "defaultVariationKey": "standard",
                                  "percentageRollout": [
                                    {"variationKey": "standard", "weightBuckets": 50000},
                                    {"variationKey": "staff", "weightBuckets": 50000}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("NUMBER"));

        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "ui-banner-config",
                                  "name": "UI Banner JSON Config",
                                  "type": "JSON",
                                  "variations": [
                                    {"key": "v1", "type": "JSON", "value": "{\\"theme\\":\\"dark\\"}"}
                                  ],
                                  "defaultVariationKey": "v1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("JSON"));

        // Update flag to 100% rollout and verify kill-switch toggle clears and restores fullRolloutSince
        mockMvc.perform(put("/api/v1/projects/catalog/environments/dev/flags/max-cart-items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Max Cart Items Updated",
                                  "owner": "cart-team",
                                  "enabled": true,
                                  "variations": [
                                    {"key": "standard", "type": "NUMBER", "value": 30},
                                    {"key": "staff", "type": "NUMBER", "value": 200}
                                  ],
                                  "defaultVariationKey": "standard",
                                  "rolloutPercentage": 100.0,
                                  "rolloutVariationKey": "staff"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.fullRolloutSince").exists());

        mockMvc.perform(patch("/api/v1/projects/catalog/environments/dev/flags/max-cart-items/kill-switch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"killSwitch\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.killSwitch").value(true))
                .andExpect(jsonPath("$.fullRolloutSince").doesNotExist());

        mockMvc.perform(patch("/api/v1/projects/catalog/environments/dev/flags/max-cart-items/kill-switch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"killSwitch\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.killSwitch").value(false))
                .andExpect(jsonPath("$.fullRolloutSince").exists());

        // Reject invalid segment and flag payloads with 400 ProblemDetail
        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/segments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"null-user-seg\",\"name\":\"Null User Seg\",\"includedUsers\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "mismatched-value-type",
                                  "name": "Mismatched Value Type",
                                  "type": "BOOLEAN",
                                  "variations": [{"key":"on","type":"BOOLEAN","value":"not-a-boolean"}],
                                  "defaultVariationKey": "on"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "missing-rollout-var",
                                  "name": "Missing Rollout Var",
                                  "type": "BOOLEAN",
                                  "variations": [{"key":"on","value":true}],
                                  "defaultVariationKey": "on",
                                  "rolloutPercentage": 50.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Evaluate with TYPE_MISMATCH and FLAG_NOT_FOUND
        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/flags/max-cart-items/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"u1\",\"expectedType\":\"BOOLEAN\",\"fallbackValue\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.value").value(false));

        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/flags/non-existent-flag/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKey\":\"u1\",\"expectedType\":\"STRING\",\"fallbackValue\":\"fallback-str\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"))
                .andExpect(jsonPath("$.value").value("fallback-str"));

        // Invalid variation reference in flag creation returns 400 ProblemDetail
        mockMvc.perform(post("/api/v1/projects/catalog/environments/dev/flags")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "bad-default",
                                  "name": "Bad Default",
                                  "type": "BOOLEAN",
                                  "variations": [{"key":"on","type":"BOOLEAN","value":true}],
                                  "defaultVariationKey": "missing-key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Request Argument"));

        // List flags (with staleOnly=false and staleOnly=true)
        mockMvc.perform(get("/api/v1/projects/catalog/environments/dev/flags?staleOnly=false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/projects/catalog/environments/dev/flags?staleOnly=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Delete flags, segment, and project
        mockMvc.perform(delete("/api/v1/projects/catalog/environments/dev/flags/ui-banner-config")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/projects/catalog/environments/dev/segments/staff-users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/projects/catalog")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    private String obtainJwtToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = json.get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
