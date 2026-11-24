package com.flagship.starter.exception;

/**
 * Thrown when a method annotated with {@link com.flagship.starter.annotation.FeatureGuard}
 * is invoked while the underlying feature flag is disabled and no fallback method is defined.
 */
public class FeatureDisabledException extends RuntimeException {

    private final String flagKey;

    public FeatureDisabledException(String flagKey) {
        super("Feature flag '" + flagKey + "' is disabled");
        this.flagKey = flagKey;
    }

    public FeatureDisabledException(String flagKey, String message) {
        super(message);
        this.flagKey = flagKey;
    }

    public String getFlagKey() {
        return flagKey;
    }
}
