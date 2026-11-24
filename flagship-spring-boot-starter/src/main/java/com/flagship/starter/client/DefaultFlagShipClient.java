package com.flagship.starter.client;

import com.flagship.core.evaluation.FlagEvaluationEngine;
import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.VariationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultFlagShipClient implements FlagShipClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultFlagShipClient.class);

    private final FlagEvaluationEngine evaluationEngine;
    private final AtomicReference<EnvironmentSnapshot> snapshotRef = new AtomicReference<>();
    private final List<FlagChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private volatile Instant lastSyncTime;

    public DefaultFlagShipClient(FlagEvaluationEngine evaluationEngine, EnvironmentSnapshot initialSnapshot) {
        this.evaluationEngine = Objects.requireNonNull(evaluationEngine, "evaluationEngine must not be null");
        if (initialSnapshot != null) {
            this.snapshotRef.set(initialSnapshot);
            this.lastSyncTime = Instant.now();
        }
    }

    public void updateSnapshot(EnvironmentSnapshot newSnapshot) {
        if (newSnapshot == null) {
            return;
        }

        EnvironmentSnapshot oldSnapshot = this.snapshotRef.getAndSet(newSnapshot);
        this.lastSyncTime = Instant.now();
        notifyChanges(oldSnapshot, newSnapshot);
    }

    private void notifyChanges(EnvironmentSnapshot oldSnapshot, EnvironmentSnapshot newSnapshot) {
        if (changeListeners.isEmpty()) {
            return;
        }

        Map<String, FlagDefinition> oldFlags = oldSnapshot != null ? oldSnapshot.flags() : Map.of();
        Map<String, FlagDefinition> newFlags = newSnapshot.flags();

        for (Map.Entry<String, FlagDefinition> entry : newFlags.entrySet()) {
            String flagKey = entry.getKey();
            FlagDefinition newDef = entry.getValue();
            FlagDefinition oldDef = oldFlags.get(flagKey);

            if (oldDef == null || !oldDef.equals(newDef)) {
                for (FlagChangeListener listener : changeListeners) {
                    try {
                        listener.onFlagChanged(flagKey, newDef);
                    } catch (Exception ex) {
                        log.warn("Error notifying FlagChangeListener for key '{}'", flagKey, ex);
                    }
                }
            }
        }
    }

    @Override
    public boolean isEnabled(String flagKey, EvaluationContext context, boolean fallback) {
        EnvironmentSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) return fallback;
        EvaluationResult result = evaluationEngine.evaluate(snapshot, flagKey, context, VariationType.BOOLEAN, fallback);
        return result.asBoolean(fallback);
    }

    @Override
    public String getString(String flagKey, EvaluationContext context, String fallback) {
        EnvironmentSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) return fallback;
        EvaluationResult result = evaluationEngine.evaluate(snapshot, flagKey, context, VariationType.STRING, fallback);
        return result.asString(fallback);
    }

    @Override
    public int getInteger(String flagKey, EvaluationContext context, int fallback) {
        EnvironmentSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) return fallback;
        EvaluationResult result = evaluationEngine.evaluate(snapshot, flagKey, context, VariationType.NUMBER, fallback);
        return (int) result.asLong(fallback);
    }

    @Override
    public double getDouble(String flagKey, EvaluationContext context, double fallback) {
        EnvironmentSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) return fallback;
        EvaluationResult result = evaluationEngine.evaluate(snapshot, flagKey, context, VariationType.NUMBER, fallback);
        return result.asDouble(fallback);
    }

    @Override
    public EvaluationResult evaluate(String flagKey, EvaluationContext context) {
        EnvironmentSnapshot snapshot = snapshotRef.get();
        if (snapshot == null) {
            return new EvaluationResult(
                    flagKey,
                    null,
                    VariationType.STRING,
                    null,
                    EvaluationReason.FLAG_NOT_FOUND,
                    null,
                    EvaluationResult.NO_BUCKET,
                    0L,
                    Instant.now()
            );
        }
        return evaluationEngine.evaluate(snapshot, flagKey, context, null, null);
    }

    @Override
    public EnvironmentSnapshot getCurrentSnapshot() {
        return snapshotRef.get();
    }

    @Override
    public Instant getLastSyncTime() {
        return lastSyncTime;
    }

    @Override
    public void addChangeListener(FlagChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    @Override
    public void removeChangeListener(FlagChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    @Override
    public void close() {
        changeListeners.clear();
    }
}
