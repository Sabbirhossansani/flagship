package com.flagship.starter.client;

import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.VariationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Resilient Decorator/Proxy around {@link FlagShipClient} guaranteeing fail-safe execution.
 * <p>
 * Catches all unexpected exceptions during evaluation, increments Micrometer counters,
 * logs diagnostic warnings, and seamlessly returns caller fallback values.
 */
public class ResilientFlagShipClient implements FlagShipClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientFlagShipClient.class);

    private final FlagShipClient delegate;
    private final MeterRegistry meterRegistry;

    public ResilientFlagShipClient(FlagShipClient delegate, MeterRegistry meterRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate FlagShipClient must not be null");
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean isEnabled(String flagKey, EvaluationContext context, boolean fallback) {
        long start = System.nanoTime();
        try {
            boolean value = delegate.isEnabled(flagKey, context, fallback);
            recordSuccess(flagKey, "BOOLEAN", start);
            return value;
        } catch (Throwable t) {
            recordFallback(flagKey, "BOOLEAN", t);
            return fallback;
        }
    }

    @Override
    public String getString(String flagKey, EvaluationContext context, String fallback) {
        long start = System.nanoTime();
        try {
            String value = delegate.getString(flagKey, context, fallback);
            recordSuccess(flagKey, "STRING", start);
            return value;
        } catch (Throwable t) {
            recordFallback(flagKey, "STRING", t);
            return fallback;
        }
    }

    @Override
    public int getInteger(String flagKey, EvaluationContext context, int fallback) {
        long start = System.nanoTime();
        try {
            int value = delegate.getInteger(flagKey, context, fallback);
            recordSuccess(flagKey, "INTEGER", start);
            return value;
        } catch (Throwable t) {
            recordFallback(flagKey, "INTEGER", t);
            return fallback;
        }
    }

    @Override
    public double getDouble(String flagKey, EvaluationContext context, double fallback) {
        long start = System.nanoTime();
        try {
            double value = delegate.getDouble(flagKey, context, fallback);
            recordSuccess(flagKey, "DOUBLE", start);
            return value;
        } catch (Throwable t) {
            recordFallback(flagKey, "DOUBLE", t);
            return fallback;
        }
    }

    @Override
    public EvaluationResult evaluate(String flagKey, EvaluationContext context) {
        long start = System.nanoTime();
        try {
            EvaluationResult result = delegate.evaluate(flagKey, context);
            recordSuccess(flagKey, "GENERIC", start);
            return result;
        } catch (Throwable t) {
            recordFallback(flagKey, "GENERIC", t);
            return new EvaluationResult(
                    flagKey,
                    null,
                    VariationType.STRING,
                    null,
                    EvaluationReason.ERROR,
                    null,
                    EvaluationResult.NO_BUCKET,
                    0L,
                    Instant.now()
            );
        }
    }

    @Override
    public EnvironmentSnapshot getCurrentSnapshot() {
        return delegate.getCurrentSnapshot();
    }

    @Override
    public Instant getLastSyncTime() {
        return delegate.getLastSyncTime();
    }

    @Override
    public void addChangeListener(FlagChangeListener listener) {
        delegate.addChangeListener(listener);
    }

    @Override
    public void removeChangeListener(FlagChangeListener listener) {
        delegate.removeChangeListener(listener);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private void recordSuccess(String flagKey, String type, long startNanos) {
        if (meterRegistry != null) {
            try {
                meterRegistry.counter("flagship.evaluations.total", "flag", flagKey, "type", type, "status", "success").increment();
                meterRegistry.timer("flagship.evaluation.latency", "flag", flagKey)
                        .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    private void recordFallback(String flagKey, String type, Throwable t) {
        log.warn("Resilient fallback triggered for flag '{}' (type={}): {}", flagKey, type, t.getMessage());
        if (meterRegistry != null) {
            try {
                meterRegistry.counter("flagship.evaluations.total", "flag", flagKey, "type", type, "status", "fallback").increment();
                meterRegistry.counter("flagship.evaluation.fallback", "flag", flagKey, "exception", t.getClass().getSimpleName()).increment();
            } catch (Exception ignored) {
            }
        }
    }
}
