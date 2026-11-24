package com.flagship.starter.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.SegmentDefinition;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.RuleNode;
import com.flagship.starter.client.DefaultFlagShipClient;
import com.flagship.starter.config.FlagshipStarterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages dual real-time SSE streaming and scheduled polling fallback synchronization (FR5).
 */
public class FlagshipSyncEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FlagshipSyncEngine.class);

    private final FlagshipStarterProperties properties;
    private final DefaultFlagShipClient client;
    private final DiskPersistenceManager diskPersistenceManager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "flagship-sync-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService sseWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "flagship-sse-worker");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean sseConnected = new AtomicBoolean(false);
    private volatile long currentRevision = 0;

    public FlagshipSyncEngine(
            FlagshipStarterProperties properties,
            DefaultFlagShipClient client,
            DiskPersistenceManager diskPersistenceManager
    ) {
        this.properties = properties;
        this.client = client;
        this.diskPersistenceManager = diskPersistenceManager;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectionTimeout())
                .build();
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            // 1. Cold-start load: Try remote first, fallback to disk
            boolean fetched = syncSnapshotFromRemote();
            if (!fetched) {
                EnvironmentSnapshot cached = diskPersistenceManager.load();
                if (cached != null) {
                    client.updateSnapshot(cached);
                    currentRevision = cached.revision();
                    log.info("Cold-start initialized using local disk cache (rev={})", currentRevision);
                } else {
                    log.warn("Cold-start: server unreachable and no disk cache found. Client initialized in empty state.");
                }
            }

            // 2. Start SSE Stream if enabled
            if (properties.isSseEnabled()) {
                sseWorker.submit(this::runSseLoop);
            }

            // 3. Schedule polling fallback
            long pollSec = Math.max(5, properties.getPollingInterval().toSeconds());
            scheduler.scheduleWithFixedDelay(this::pollFallbackTask, pollSec, pollSec, TimeUnit.SECONDS);
        }
    }

    public boolean isSseConnected() {
        return sseConnected.get();
    }

    public boolean syncSnapshotFromRemote() {
        String baseUrl = properties.getServerUrl();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String url = String.format("%s/api/v1/projects/%s/environments/%s/snapshot",
                baseUrl, properties.getProjectKey(), properties.getEnvironmentKey());

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(properties.getConnectionTimeout())
                    .GET();

            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                reqBuilder.header("X-API-Key", properties.getApiKey());
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                EnvironmentSnapshot snapshot = parseSnapshotResponse(resp.body());
                if (snapshot != null) {
                    currentRevision = snapshot.revision();
                    client.updateSnapshot(snapshot);
                    diskPersistenceManager.persist(snapshot);
                    log.debug("Successfully synced snapshot revision {} from {}", currentRevision, url);
                    return true;
                }
            } else {
                log.warn("Snapshot fetch returned HTTP {} from {}", resp.statusCode(), url);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch snapshot from {}: {}", url, ex.getMessage());
        }
        return false;
    }

    private void runSseLoop() {
        int backoffSec = 2;
        while (running.get()) {
            try {
                connectAndListenSse();
                backoffSec = 2; // Reset on clean connection
            } catch (Exception ex) {
                sseConnected.set(false);
                if (running.get()) {
                    log.warn("SSE stream disconnected ({}), reconnecting in {}s...", ex.getMessage(), backoffSec);
                    try {
                        TimeUnit.SECONDS.sleep(backoffSec);
                        backoffSec = Math.min(30, backoffSec * 2);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void connectAndListenSse() throws Exception {
        String baseUrl = properties.getServerUrl();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String url = String.format("%s/api/v1/projects/%s/environments/%s/stream",
                baseUrl, properties.getProjectKey(), properties.getEnvironmentKey());

        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            url += "?apiKey=" + properties.getApiKey();
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .GET();

        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            reqBuilder.header("X-API-Key", properties.getApiKey());
        }

        HttpResponse<InputStream> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("SSE endpoint returned HTTP " + response.statusCode());
        }

        sseConnected.set(true);
        log.info("Connected to FlagShip real-time SSE stream at {}", url);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            String currentEvent = "message";
            StringBuilder dataBuffer = new StringBuilder();

            while (running.get() && (line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    dataBuffer.append(line.substring(5).trim());
                } else if (line.startsWith(":")) {
                    // SSE keepalive ping comment
                } else if (line.isEmpty()) {
                    if (dataBuffer.length() > 0) {
                        handleSseEvent(currentEvent, dataBuffer.toString());
                        dataBuffer.setLength(0);
                    }
                    currentEvent = "message";
                }
            }
        } finally {
            sseConnected.set(false);
        }
    }

    private void handleSseEvent(String eventName, String data) {
        log.debug("Received SSE event '{}': {}", eventName, data);
        if ("connected".equalsIgnoreCase(eventName)) {
            return;
        }

        // On any flag_update, segment_update, or snapshot, sync fresh snapshot
        try {
            syncSnapshotFromRemote();
        } catch (Exception ex) {
            log.error("Failed to sync snapshot upon SSE event '{}'", eventName, ex);
        }
    }

    private void pollFallbackTask() {
        if (!running.get()) return;

        // If SSE is connected and healthy, polling is not strictly needed, but periodic sanity sync is harmless
        if (!sseConnected.get()) {
            log.debug("SSE disconnected; executing scheduled polling fallback sync...");
            syncSnapshotFromRemote();
        }
    }

    private EnvironmentSnapshot parseSnapshotResponse(String json) {
        try {
            return FlagshipSnapshotParser.parse(json, properties.getProjectKey(), properties.getEnvironmentKey());
        } catch (Exception e) {
            log.error("Failed to parse snapshot response: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            sseConnected.set(false);
            scheduler.shutdownNow();
            sseWorker.shutdownNow();
        }
    }
}
