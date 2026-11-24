package com.flagship.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Spring Boot configuration properties for FlagShip Starter (`flagship.*`).
 */
@ConfigurationProperties(prefix = "flagship")
public class FlagshipStarterProperties {

    /**
     * Whether FlagShip client auto-configuration is enabled.
     */
    private boolean enabled = true;

    /**
     * Control plane backend server URL (e.g. {@code http://localhost:8080}).
     */
    private String serverUrl = "http://localhost:8080";

    /**
     * Project key matching the registered project in FlagShip Server.
     */
    private String projectKey = "default";

    /**
     * Target environment key (e.g. {@code production}, {@code staging}, {@code development}).
     */
    private String environmentKey = "production";

    /**
     * Authentication API key or SDK token.
     */
    private String apiKey;

    /**
     * Scheduled polling fallback interval when SSE is unavailable.
     */
    private Duration pollingInterval = Duration.ofSeconds(30);

    /**
     * Whether real-time SSE streaming updates are enabled.
     */
    private boolean sseEnabled = true;

    /**
     * Whether local disk caching is enabled for zero-downtime offline starts (FR6).
     */
    private boolean diskPersistenceEnabled = true;

    /**
     * File path where the in-memory snapshot cache is persisted on disk.
     */
    private String diskPersistencePath = "./.flagship-cache.json";

    /**
     * Network connect/read timeout for HTTP requests.
     */
    private Duration connectionTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getEnvironmentKey() {
        return environmentKey;
    }

    public void setEnvironmentKey(String environmentKey) {
        this.environmentKey = environmentKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(Duration pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public boolean isSseEnabled() {
        return sseEnabled;
    }

    public void setSseEnabled(boolean sseEnabled) {
        this.sseEnabled = sseEnabled;
    }

    public boolean isDiskPersistenceEnabled() {
        return diskPersistenceEnabled;
    }

    public void setDiskPersistenceEnabled(boolean diskPersistenceEnabled) {
        this.diskPersistenceEnabled = diskPersistenceEnabled;
    }

    public String getDiskPersistencePath() {
        return diskPersistencePath;
    }

    public void setDiskPersistencePath(String diskPersistencePath) {
        this.diskPersistencePath = diskPersistencePath;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
}
