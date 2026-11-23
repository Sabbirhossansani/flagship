package com.flagship.server.service;

import com.flagship.core.model.ProjectRole;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.CompositeRuleNode;
import com.flagship.core.rule.LeafRuleNode;
import com.flagship.core.rule.LogicalOperator;
import com.flagship.core.rule.RuleDescribeVisitor;
import com.flagship.core.rule.RuleNode;
import com.flagship.core.rule.RuleOperatorType;
import com.flagship.core.rule.SegmentReferenceNode;
import com.flagship.server.config.FlagshipServerProperties;
import com.flagship.server.domain.FlagEntity;
import com.flagship.server.domain.ProjectRoleAssignmentEntity;
import com.flagship.server.domain.ProjectRoleAssignmentRepository;
import com.flagship.server.dto.PageResponse;
import com.flagship.server.dto.RuleNodeDto;
import com.flagship.server.dto.TargetingRuleDto;
import com.flagship.server.security.JwtTokenService;
import com.flagship.server.security.ProjectAccessEvaluator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainJsonCodecAndJwtUnitTest {

    private final DomainJsonCodec codec = new DomainJsonCodec(new RuleDescribeVisitor());

    @Test
    void roundTripsNestedCompositeRuleTreeAndTargetingRules() {
        RuleNode originalTree = CompositeRuleNode.and(
                LeafRuleNode.of("country", RuleOperatorType.IN, "BD", "US"),
                CompositeRuleNode.or(
                        LeafRuleNode.of("appVersion", RuleOperatorType.SEMVER_GT, "2.1.0"),
                        SegmentReferenceNode.of("beta-testers")
                )
        );

        RuleNodeDto dto = RuleNodeDto.fromDomain(originalTree);
        String json = codec.writeRuleNode(dto);
        RuleNode restored = codec.readRuleNode(json);

        assertThat(restored).isEqualTo(originalTree);

        TargetingRule domainRule = TargetingRule.ofRollout(
                "rule-1",
                "Canary rule",
                originalTree,
                List.of(new WeightedVariation("v1", 30), new WeightedVariation("v2", 70))
        );
        TargetingRuleDto ruleDto = TargetingRuleDto.fromDomain(domainRule, new RuleDescribeVisitor());
        assertThat(ruleDto.conditionDescription()).contains("country IN [BD, US]");

        String rulesJson = codec.writeRules(List.of(ruleDto));
        List<TargetingRule> restoredRules = codec.readDomainRules(rulesJson);
        assertThat(restoredRules).hasSize(1);
        assertThat(restoredRules.get(0).id()).isEqualTo("rule-1");
        assertThat(restoredRules.get(0).rolloutDistributions()).hasSize(2);
    }

    @Test
    void convertsFlagEntityToCoreDomainAndFlagResponseWithStaleCheck() {
        FlagEntity entity = new FlagEntity();
        entity.setEnvKey("production");
        entity.setFlagKey("checkout-v2");
        entity.setName("Checkout V2");
        entity.setDescription("New checkout pipeline");
        entity.setOwner("payments-team");
        entity.setVariationType(VariationType.BOOLEAN);
        entity.setEnabled(true);
        entity.setKillSwitch(false);
        entity.setVariationsJson(codec.writeVariations(List.of(
                Variation.ofBoolean("on", true),
                Variation.ofBoolean("off", false)
        )));
        entity.setDefaultVariationKey("on");
        entity.setOffVariationKey("off");
        entity.setIndividualTargetsJson(codec.writeStringMap(Map.of("vip-1", "on")));
        entity.setRulesJson("[]");
        entity.setRolloutPercentage(100.0);
        entity.setPercentageRolloutJson("[]");
        entity.setVersion(4L);
        Instant twentyDaysAgo = Instant.now().minusSeconds(20L * 86400L);
        entity.setCreatedAt(twentyDaysAgo);
        entity.setUpdatedAt(twentyDaysAgo);
        entity.setFullRolloutSince(twentyDaysAgo);

        var flagResponse = codec.toFlagResponse("commerce", entity);
        assertThat(flagResponse.key()).isEqualTo("checkout-v2");
        assertThat(flagResponse.stale()).isTrue();
        assertThat(flagResponse.variations()).hasSize(2);
    }

    @Test
    void jwtTokenServiceGeneratesAndVerifiesValidTokenAndRejectsTamperedToken() {
        FlagshipServerProperties props = new FlagshipServerProperties();
        props.getSecurity().setJwtSecret("0123456789abcdef0123456789abcdef-secret");
        props.getSecurity().setJwtExpirationSeconds(600);

        JwtTokenService jwtService = new JwtTokenService(props);
        String token = jwtService.generateToken("alice", List.of("ROLE_EDITOR"));

        Optional<JwtTokenService.JwtClaims> verified = jwtService.verifyToken(token);
        assertThat(verified).isPresent();
        assertThat(verified.get().username()).isEqualTo("alice");
        assertThat(verified.get().roles()).containsExactly("ROLE_EDITOR");

        // Tamper with signature
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(jwtService.verifyToken(tampered)).isEmpty();
        assertThat(jwtService.verifyToken("malformed.token")).isEmpty();
    }

    @Test
    void projectAccessEvaluatorHonorsPerProjectRoleOverrideAndGlobalFallback() {
        ProjectRoleAssignmentRepository repo = Mockito.mock(ProjectRoleAssignmentRepository.class);
        Mockito.when(repo.findByProjectKeyAndUsername("alpha", "bob"))
                .thenReturn(Optional.of(new ProjectRoleAssignmentEntity(
                        "alpha", "bob", ProjectRole.ADMIN, Instant.now()
                )));
        Mockito.when(repo.findByProjectKeyAndUsername("beta", "bob"))
                .thenReturn(Optional.empty());

        ProjectAccessEvaluator evaluator = new ProjectAccessEvaluator(repo);
        var bobViewerAuth = new UsernamePasswordAuthenticationToken(
                "bob", null, List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))
        );

        // On project 'alpha', bob has explicit ADMIN override
        assertThat(evaluator.isAdmin(bobViewerAuth, "alpha")).isTrue();
        assertThat(evaluator.canEdit(bobViewerAuth, "alpha")).isTrue();

        // On project 'beta', bob falls back to global ROLE_VIEWER
        assertThat(evaluator.canView(bobViewerAuth, "beta")).isTrue();
        assertThat(evaluator.canEdit(bobViewerAuth, "beta")).isFalse();
        assertThat(evaluator.isAdmin(bobViewerAuth, "beta")).isFalse();
    }

    @Test
    void pageResponseOfListSlicesAccurately() {
        List<String> items = List.of("a", "b", "c", "d", "e");
        PageResponse<String> page0 = PageResponse.ofList(items, 0, 2);
        assertThat(page0.content()).containsExactly("a", "b");
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.last()).isFalse();

        PageResponse<String> page2 = PageResponse.ofList(items, 2, 2);
        assertThat(page2.content()).containsExactly("e");
        assertThat(page2.last()).isTrue();
    }

    @Test
    void rejectsNullElementsInRuleNodeDtoAndTargetingRuleDtoWithIllegalArgumentException() {
        RuleNodeDto compositeWithNullChild = RuleNodeDto.composite(
                LogicalOperator.AND,
                Arrays.asList((RuleNodeDto) null)
        );
        assertThatThrownBy(compositeWithNullChild::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain null elements");

        RuleNodeDto leafWithNullValue = RuleNodeDto.leaf(
                "country",
                RuleOperatorType.IN,
                Arrays.asList("BD", null)
        );
        assertThatThrownBy(leafWithNullValue::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain null elements");

        TargetingRuleDto ruleWithNullWeight = new TargetingRuleDto(
                "r1", "Rule 1", "seg1", null, null, null, Arrays.asList((WeightedVariation) null)
        );
        assertThatThrownBy(ruleWithNullWeight::toDomain)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain null elements");

        assertThat(codec.readStringSet("[null, \"\", \"valid-user\"]"))
                .containsExactly("valid-user");
    }
}
