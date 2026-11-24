package com.flagship.server.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentEventRepository extends JpaRepository<ExperimentEventEntity, Long> {

    List<ExperimentEventEntity> findAllByProjectKeyAndEnvKeyAndFlagKey(
            String projectKey,
            String envKey,
            String flagKey
    );

    @Query("""
            SELECT e.variationKey, COUNT(DISTINCT e.userKey)
            FROM ExperimentEventEntity e
            WHERE e.projectKey = :projectKey
              AND e.envKey = :envKey
              AND e.flagKey = :flagKey
              AND e.eventType = 'EXPOSURE'
              AND e.variationKey IS NOT NULL
            GROUP BY e.variationKey
            """)
    List<Object[]> countDistinctExposuresByVariation(
            @Param("projectKey") String projectKey,
            @Param("envKey") String envKey,
            @Param("flagKey") String flagKey
    );

    @Query("""
            SELECT e.variationKey, COUNT(DISTINCT e.userKey), SUM(e.eventValue)
            FROM ExperimentEventEntity e
            WHERE e.projectKey = :projectKey
              AND e.envKey = :envKey
              AND e.flagKey = :flagKey
              AND e.eventType = 'GOAL'
              AND (:metricName IS NULL OR e.metricName = :metricName)
              AND e.variationKey IS NOT NULL
            GROUP BY e.variationKey
            """)
    List<Object[]> countDistinctConversionsByVariation(
            @Param("projectKey") String projectKey,
            @Param("envKey") String envKey,
            @Param("flagKey") String flagKey,
            @Param("metricName") String metricName
    );
}
