package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark measuring 10-rule flag evaluation latency in microseconds (QA1).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class FlagEvaluationJmhBenchmark {

    private FlagEvaluationEngine engine;
    private FlagDefinition tenRuleFlag;
    private EvaluationContext context;

    @Setup(Level.Trial)
    public void setUp() {
        engine = new FlagEvaluationEngine();
        tenRuleFlag = Qa1PerformanceBenchmarkTest.createTenRuleFlag("jmh-10-rule-flag");
        context = EvaluationContext.builder("jmh-user-1")
                .attribute("region", "APAC")
                .attribute("appVersion", "3.4.0")
                .attribute("email", "engineer@flagship.internal")
                .attribute("signupDate", "2026-04-15T10:00:00Z")
                .evaluationTime(Instant.EPOCH)
                .build();
    }

    @Benchmark
    public EvaluationResult evaluateTenRuleFlag() {
        return engine.evaluate(tenRuleFlag, context);
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(FlagEvaluationJmhBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(options).run();
    }
}
