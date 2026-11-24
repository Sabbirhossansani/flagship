package com.flagship.starter.client;

import com.flagship.core.model.FlagDefinition;

/**
 * Observer interface for reacting to runtime feature flag updates (FR5).
 */
@FunctionalInterface
public interface FlagChangeListener {

    /**
     * Invoked when a flag definition is updated or reloaded in the active client snapshot.
     *
     * @param flagKey       the key of the modified flag
     * @param newDefinition the updated flag definition (or {@code null} if deleted)
     */
    void onFlagChanged(String flagKey, FlagDefinition newDefinition);
}
