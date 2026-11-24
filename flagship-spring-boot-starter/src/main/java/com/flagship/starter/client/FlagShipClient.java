package com.flagship.starter.client;

import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationResult;

import java.io.Closeable;
import java.time.Instant;

/**
 * Primary client interface for in-memory, zero-network feature flag evaluation in Spring Boot applications.
 */
public interface FlagShipClient extends Closeable {

    boolean isEnabled(String flagKey, EvaluationContext context, boolean fallback);

    default boolean isEnabled(String flagKey, boolean fallback) {
        return isEnabled(flagKey, EvaluationContext.builder().build(), fallback);
    }

    String getString(String flagKey, EvaluationContext context, String fallback);

    default String getString(String flagKey, String fallback) {
        return getString(flagKey, EvaluationContext.builder().build(), fallback);
    }

    int getInteger(String flagKey, EvaluationContext context, int fallback);

    default int getInteger(String flagKey, int fallback) {
        return getInteger(flagKey, EvaluationContext.builder().build(), fallback);
    }

    double getDouble(String flagKey, EvaluationContext context, double fallback);

    default double getDouble(String flagKey, double fallback) {
        return getDouble(flagKey, EvaluationContext.builder().build(), fallback);
    }

    EvaluationResult evaluate(String flagKey, EvaluationContext context);

    EnvironmentSnapshot getCurrentSnapshot();

    Instant getLastSyncTime();

    void addChangeListener(FlagChangeListener listener);

    void removeChangeListener(FlagChangeListener listener);

    @Override
    void close();
}
