package com.flagship.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable context passed to the feature flag evaluation engine (FR2, FR3).
 * Implements the mandatory GoF Builder pattern ({@link EvaluationContext#builder()}).
 */
public final class EvaluationContext {

    private final String userKey;
    private final Map<String, Object> attributes;
    private final Instant evaluationTime;

    private EvaluationContext(Builder builder) {
        this.userKey = builder.userKey;
        this.attributes = builder.attributes.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.evaluationTime = builder.evaluationTime != null ? builder.evaluationTime : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String userKey) {
        return new Builder().userKey(userKey);
    }

    public static EvaluationContext ofUser(String userKey) {
        return new Builder().userKey(userKey).build();
    }

    public String getUserKey() {
        return userKey;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String key) {
        if ("userKey".equals(key) || "key".equals(key)) {
            Object explicit = attributes.get(key);
            return explicit != null ? explicit : userKey;
        }
        return attributes.get(key);
    }

    public Instant getEvaluationTime() {
        return evaluationTime;
    }

    /**
     * GoF Builder for constructing immutable {@link EvaluationContext} instances.
     */
    public static final class Builder {
        private String userKey;
        private final Map<String, Object> attributes = new HashMap<>();
        private Instant evaluationTime;

        private Builder() {
        }

        public Builder userKey(String userKey) {
            this.userKey = userKey;
            return this;
        }

        public Builder attribute(String key, Object value) {
            Objects.requireNonNull(key, "attribute key must not be null");
            if (value == null) {
                this.attributes.remove(key);
            } else {
                this.attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, ?> attributes) {
            if (attributes != null) {
                for (Map.Entry<String, ?> entry : attributes.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        this.attributes.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            return this;
        }

        public Builder evaluationTime(Instant evaluationTime) {
            this.evaluationTime = evaluationTime;
            return this;
        }

        public EvaluationContext build() {
            return new EvaluationContext(this);
        }
    }
}
