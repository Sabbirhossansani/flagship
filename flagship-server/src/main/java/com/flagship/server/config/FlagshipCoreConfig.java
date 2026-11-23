package com.flagship.server.config;

import com.flagship.core.evaluation.FlagEvaluationEngine;
import com.flagship.core.rule.RuleDescribeVisitor;
import com.flagship.core.rule.RuleValidationVisitor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers shared stateless beans from {@code flagship-core}.
 */
@Configuration
public class FlagshipCoreConfig {

    @Bean
    public FlagEvaluationEngine flagEvaluationEngine() {
        return new FlagEvaluationEngine();
    }

    @Bean
    public RuleValidationVisitor ruleValidationVisitor() {
        return new RuleValidationVisitor();
    }

    @Bean
    public RuleDescribeVisitor ruleDescribeVisitor() {
        return new RuleDescribeVisitor();
    }
}
