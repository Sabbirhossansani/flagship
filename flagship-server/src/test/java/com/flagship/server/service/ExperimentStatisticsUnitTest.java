package com.flagship.server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExperimentStatisticsUnitTest {

    private final ExperimentStatisticsService service = new ExperimentStatisticsService();

    @Test
    @DisplayName("SRM: Balanced traffic yields high p-value and hasSrm = false")
    void balancedTraffic_noSrm() {
        List<String> variations = List.of("control", "treatment");
        Map<String, Long> observed = Map.of("control", 5020L, "treatment", 4980L);
        Map<String, Double> weights = Map.of("control", 1.0, "treatment", 1.0);

        var result = service.evaluateSrm(variations, observed, weights);

        assertThat(result.hasSrm()).isFalse();
        assertThat(result.chiSquare()).isLessThan(1.0);
        assertThat(result.pValue()).isGreaterThan(0.30);
    }

    @Test
    @DisplayName("SRM: Highly skewed traffic triggers SRM detection with p < 0.01")
    void skewedTraffic_triggersSrm() {
        List<String> variations = List.of("control", "treatment");
        Map<String, Long> observed = Map.of("control", 6000L, "treatment", 4000L);
        Map<String, Double> weights = Map.of("control", 1.0, "treatment", 1.0);

        var result = service.evaluateSrm(variations, observed, weights);

        assertThat(result.hasSrm()).isTrue();
        assertThat(result.chiSquare()).isGreaterThan(100.0);
        assertThat(result.pValue()).isLessThan(0.001);
        assertThat(result.warningMessage()).contains("Sample Ratio Mismatch detected");
    }

    @Test
    @DisplayName("Two-proportion Z-test: Significant uplift detected")
    void zTest_significantUplift() {
        // Baseline: 100 / 1000 (10%)
        // Treatment: 160 / 1000 (16%)
        var zTest = service.computeTwoProportionZTest(100, 1000, 160, 1000);

        assertThat(zTest.zScore()).isGreaterThan(3.5);
        assertThat(zTest.pValue()).isLessThan(0.001);
        assertThat(zTest.statisticallySignificant()).isTrue();
        assertThat(zTest.ciLower()).isGreaterThan(0.02);
    }

    @Test
    @DisplayName("Two-proportion Z-test: Insignificant difference yields p > 0.05")
    void zTest_insignificantDifference() {
        // Baseline: 100 / 1000 (10%)
        // Treatment: 102 / 1000 (10.2%)
        var zTest = service.computeTwoProportionZTest(100, 1000, 102, 1000);

        assertThat(zTest.statisticallySignificant()).isFalse();
        assertThat(zTest.pValue()).isGreaterThan(0.80);
    }

    @Test
    @DisplayName("Standard normal distribution CDF accuracy")
    void standardNormalCdf_criticalValues() {
        assertThat(ExperimentStatisticsService.standardNormalCdf(0.0)).isCloseTo(0.5000, within(1e-4));
        assertThat(ExperimentStatisticsService.standardNormalCdf(1.96)).isCloseTo(0.9750, within(1e-3));
        assertThat(ExperimentStatisticsService.standardNormalCdf(-1.96)).isCloseTo(0.0250, within(1e-3));
    }
}
