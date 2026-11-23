package com.flagship.core.evaluation;

import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.SegmentDefinition;
import com.flagship.core.model.VariationType;
import com.flagship.core.strategy.BucketingStrategy;
import com.flagship.core.strategy.Murmur3BucketingStrategy;
import com.flagship.core.strategy.OperatorStrategyRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * Stateless, thread-safe local rule-evaluation engine shared by both {@code flagship-server} preview
 * and {@code flagship-spring-boot-starter} to guarantee 100% deterministic consistency (QA4)
 * and sub-50-microsecond p99 evaluation latency (QA1).
 *
 * <p>Assembles the strict FR2 Chain of Responsibility pipeline:
 * {@link KillSwitchHandler} -> {@link UserTargetHandler} -> {@link SegmentRuleHandler}
 * -> {@link RolloutHandler} -> {@link DefaultHandler}.
 */
public final class FlagEvaluationEngine {

    private final OperatorStrategyRegistry operatorRegistry;
    private final BucketingStrategy bucketingStrategy;
    private final EvaluationHandler pipelineHead;

    public FlagEvaluationEngine() {
        this(OperatorStrategyRegistry.defaultRegistry(), new Murmur3BucketingStrategy());
    }

    public FlagEvaluationEngine(
            OperatorStrategyRegistry operatorRegistry,
            BucketingStrategy bucketingStrategy
    ) {
        this.operatorRegistry = Objects.requireNonNull(operatorRegistry, "operatorRegistry must not be null");
        this.bucketingStrategy = Objects.requireNonNull(bucketingStrategy, "bucketingStrategy must not be null");
        this.pipelineHead = buildPipeline(this.operatorRegistry, this.bucketingStrategy);
    }

    private static EvaluationHandler buildPipeline(
            OperatorStrategyRegistry operatorRegistry,
            BucketingStrategy bucketingStrategy
    ) {
        KillSwitchHandler killSwitchHandler = new KillSwitchHandler();
        UserTargetHandler userTargetHandler = new UserTargetHandler();
        SegmentRuleHandler segmentRuleHandler = new SegmentRuleHandler(operatorRegistry, bucketingStrategy);
        RolloutHandler rolloutHandler = new RolloutHandler(bucketingStrategy);
        DefaultHandler defaultHandler = new DefaultHandler();

        killSwitchHandler
                .setNext(userTargetHandler)
                .setNext(segmentRuleHandler)
                .setNext(rolloutHandler)
                .setNext(defaultHandler);

        return killSwitchHandler;
    }

    public EvaluationResult evaluate(FlagDefinition flag, EvaluationContext context) {
        return evaluate(flag, context, Map.of());
    }

    public EvaluationResult evaluate(
            FlagDefinition flag,
            EvaluationContext context,
            Map<String, SegmentDefinition> segments
    ) {
        Objects.requireNonNull(flag, "flag must not be null");
        EvaluationContext safeContext = context != null ? context : EvaluationContext.builder().build();
        EvaluationRequest request = new EvaluationRequest(flag, safeContext, segments);
        return pipelineHead.handle(request);
    }

    public EvaluationResult evaluate(
            EnvironmentSnapshot snapshot,
            String flagKey,
            EvaluationContext context,
            VariationType expectedType,
            Object fallbackValue
    ) {
        EvaluationContext safeContext = context != null ? context : EvaluationContext.builder().build();
        if (snapshot == null || flagKey == null) {
            return EvaluationResult.notFound(
                    flagKey != null ? flagKey : "unknown",
                    expectedType,
                    fallbackValue,
                    safeContext.getEvaluationTime()
            );
        }
        FlagDefinition flag = snapshot.getFlag(flagKey);
        if (flag == null) {
            return EvaluationResult.notFound(
                    flagKey,
                    expectedType,
                    fallbackValue,
                    safeContext.getEvaluationTime()
            );
        }
        if (expectedType != null && flag.getType() != expectedType) {
            return EvaluationResult.typeMismatch(
                    flag,
                    expectedType,
                    fallbackValue,
                    safeContext.getEvaluationTime()
            );
        }
        EvaluationResult result = evaluate(flag, safeContext, snapshot.segments());
        if (result.reason() == EvaluationReason.ERROR && result.value() == null && fallbackValue != null) {
            return new EvaluationResult(
                    result.flagKey(),
                    result.variationKey(),
                    expectedType != null ? expectedType : result.type(),
                    fallbackValue,
                    EvaluationReason.ERROR,
                    result.matchedRuleId(),
                    result.bucket(),
                    result.flagVersion(),
                    result.evaluatedAt()
            );
        }
        return result;
    }

    public EvaluationHandler getPipelineHead() {
        return pipelineHead;
    }

    public OperatorStrategyRegistry getOperatorRegistry() {
        return operatorRegistry;
    }

    public BucketingStrategy getBucketingStrategy() {
        return bucketingStrategy;
    }
}
