package com.flagship.server.domain;

import com.flagship.core.model.ProjectRole;
import com.flagship.core.model.VariationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DataJpaTest} slice test verifying Flyway V1 migration and JPA repository queries (C5).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlagshipRepositoryDataJpaSliceTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private SegmentRepository segmentRepository;

    @Autowired
    private FlagRepository flagRepository;

    @Autowired
    private ProjectRoleAssignmentRepository roleAssignmentRepository;

    @Test
    void persistsAndQueriesProjectEnvironmentsSegmentsFlagsAndRolesViaFlywaySchema() {
        Instant now = Instant.now();
        ProjectEntity project = new ProjectEntity("retail", "Retail Platform", "Core retail project", now);
        project.addEnvironment(new EnvironmentEntity(project, "development", "Development", false, now));
        project.addEnvironment(new EnvironmentEntity(project, "production", "Production", true, now));
        ProjectEntity savedProject = projectRepository.saveAndFlush(project);

        assertThat(savedProject.getId()).isNotNull();
        assertThat(projectRepository.existsByProjectKey("retail")).isTrue();

        Page<EnvironmentEntity> envPage = environmentRepository.findAllByProject_ProjectKeyOrderByIdAsc(
                "retail", PageRequest.of(0, 10)
        );
        assertThat(envPage.getTotalElements()).isEqualTo(2);

        SegmentEntity segment = new SegmentEntity();
        segment.setProject(savedProject);
        segment.setEnvKey("production");
        segment.setSegmentKey("vip-shoppers");
        segment.setName("VIP Shoppers");
        segment.setConditionJson("{\"type\":\"LEAF\",\"attribute\":\"tier\",\"operator\":\"EQUALS\",\"values\":[\"GOLD\"]}");
        segment.setIncludedUsersJson("[\"u-1\"]");
        segment.setExcludedUsersJson("[]");
        segment.setVersion(1L);
        segment.setCreatedAt(now);
        segment.setUpdatedAt(now);
        segmentRepository.saveAndFlush(segment);

        Optional<SegmentEntity> foundSegment = segmentRepository.findByProject_ProjectKeyAndEnvKeyAndSegmentKey(
                "retail", "production", "vip-shoppers"
        );
        assertThat(foundSegment).isPresent();
        assertThat(foundSegment.get().getName()).isEqualTo("VIP Shoppers");

        FlagEntity flag = new FlagEntity();
        flag.setProject(savedProject);
        flag.setEnvKey("production");
        flag.setFlagKey("one-click-buy");
        flag.setName("One Click Buy");
        flag.setOwner("checkout-squad");
        flag.setVariationType(VariationType.BOOLEAN);
        flag.setEnabled(true);
        flag.setKillSwitch(false);
        flag.setVariationsJson("[{\"key\":\"on\",\"type\":\"BOOLEAN\",\"value\":true},{\"key\":\"off\",\"type\":\"BOOLEAN\",\"value\":false}]");
        flag.setDefaultVariationKey("on");
        flag.setOffVariationKey("off");
        flag.setIndividualTargetsJson("{}");
        flag.setRulesJson("[]");
        flag.setRolloutPercentage(50.0);
        flag.setRolloutVariationKey("on");
        flag.setPercentageRolloutJson("[]");
        flag.setVersion(1L);
        flag.setCreatedAt(now);
        flag.setUpdatedAt(now);
        flagRepository.saveAndFlush(flag);

        Page<FlagEntity> flagPage = flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
                "retail", "production", PageRequest.of(0, 10)
        );
        assertThat(flagPage.getTotalElements()).isEqualTo(1);
        assertThat(flagPage.getContent().get(0).getFlagKey()).isEqualTo("one-click-buy");

        ProjectRoleAssignmentEntity roleEntity = new ProjectRoleAssignmentEntity(
                "retail", "alice", ProjectRole.APPROVER, now
        );
        roleAssignmentRepository.saveAndFlush(roleEntity);

        assertThat(roleAssignmentRepository.findByProjectKeyAndUsername("retail", "alice"))
                .isPresent()
                .get()
                .extracting(ProjectRoleAssignmentEntity::getRole)
                .isEqualTo(ProjectRole.APPROVER);

        // Verify deletion of environment and project cleans up dependent records
        flagRepository.deleteAllByProject_ProjectKeyAndEnvKey("retail", "production");
        segmentRepository.deleteAllByProject_ProjectKeyAndEnvKey("retail", "production");
        assertThat(flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc("retail", "production"))
                .isEmpty();
        assertThat(segmentRepository.findAllByProject_ProjectKeyAndEnvKeyOrderBySegmentKeyAsc("retail", "production"))
                .isEmpty();

        roleAssignmentRepository.deleteAllByProjectKey("retail");
        assertThat(roleAssignmentRepository.findByProjectKeyAndUsername("retail", "alice")).isEmpty();
    }
}
