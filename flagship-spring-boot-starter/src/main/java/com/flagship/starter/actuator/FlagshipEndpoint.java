package com.flagship.starter.actuator;

import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.FlagDefinition;
import com.flagship.starter.client.FlagShipClient;
import com.flagship.starter.config.FlagshipStarterProperties;
import com.flagship.starter.sync.FlagshipSyncEngine;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot Actuator endpoint exposing FlagShip status, loaded flags, and sync metrics at {@code /actuator/flagship}.
 */
@Endpoint(id = "flagship")
public class FlagshipEndpoint {

    private final FlagShipClient client;
    private final FlagshipSyncEngine syncEngine;
    private final FlagshipStarterProperties properties;

    public FlagshipEndpoint(
            FlagShipClient client,
            FlagshipSyncEngine syncEngine,
            FlagshipStarterProperties properties
    ) {
        this.client = client;
        this.syncEngine = syncEngine;
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> getFlagshipInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        EnvironmentSnapshot snapshot = client.getCurrentSnapshot();

        boolean sseActive = syncEngine != null && syncEngine.isSseConnected();
        String status = sseActive ? "CONNECTED" : (snapshot != null ? "POLLING_FALLBACK" : "OFFLINE");

        info.put("status", status);
        info.put("projectKey", properties.getProjectKey());
        info.put("environmentKey", properties.getEnvironmentKey());
        info.put("sseConnected", sseActive);
        info.put("diskPersistenceEnabled", properties.isDiskPersistenceEnabled());
        info.put("lastSyncTime", client.getLastSyncTime() != null ? client.getLastSyncTime().toString() : "never");
        info.put("revision", snapshot != null ? snapshot.revision() : 0L);

        if (snapshot != null) {
            info.put("loadedFlagsCount", snapshot.flags().size());
            info.put("loadedSegmentsCount", snapshot.segments().size());

            Map<String, Map<String, Object>> flagSummaries = new LinkedHashMap<>();
            for (Map.Entry<String, FlagDefinition> entry : snapshot.flags().entrySet()) {
                FlagDefinition def = entry.getValue();
                flagSummaries.put(entry.getKey(), Map.of(
                        "type", def.getType().name(),
                        "enabled", def.isEnabled(),
                        "killSwitch", def.isKillSwitch(),
                        "version", def.getVersion()
                ));
            }
            info.put("flags", flagSummaries);
        } else {
            info.put("loadedFlagsCount", 0);
            info.put("loadedSegmentsCount", 0);
            info.put("flags", Map.of());
        }

        return info;
    }
}
