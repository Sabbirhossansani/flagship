package com.flagship.server.service;

import com.flagship.server.dto.EnvironmentSnapshotResponse;
import com.flagship.server.dto.FlagResponse;
import com.flagship.server.dto.SegmentResponse;
import com.flagship.server.dto.SseStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SseBroadcasterService {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcasterService.class);
    private static final Long EMITTER_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final Map<String, List<SseEmitter>> emittersByEnv = new ConcurrentHashMap<>();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SseEmitter subscribe(String projectKey, String envKey) {
        return subscribe(projectKey, envKey, new SseEmitter(EMITTER_TIMEOUT));
    }

    public SseEmitter subscribe(String projectKey, String envKey, SseEmitter emitter) {
        String key = buildKey(projectKey, envKey);

        List<SseEmitter> list = emittersByEnv.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(key, emitter);
        });
        emitter.onError(ex -> {
            emitter.complete();
            removeEmitter(key, emitter);
        });

        // Send initial connected handshake
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(new SseStreamMessage(
                            "CONNECTED",
                            projectKey,
                            envKey,
                            "SYSTEM",
                            "none",
                            0L,
                            Instant.now(),
                            Map.of("message", "Connected to FlagShip SSE stream")
                    ))
            );
        } catch (IOException e) {
            removeEmitter(key, emitter);
        }

        return emitter;
    }

    public void broadcastFlagUpdate(String projectKey, String envKey, String action, FlagResponse flag) {
        SseStreamMessage message = new SseStreamMessage(
                "FLAG_" + action,
                projectKey,
                envKey,
                "FLAG",
                flag.key(),
                flag.version(),
                Instant.now(),
                flag
        );
        broadcastEvent(projectKey, envKey, "flag_update", message);
    }

    public void broadcastSegmentUpdate(String projectKey, String envKey, String action, SegmentResponse segment) {
        SseStreamMessage message = new SseStreamMessage(
                "SEGMENT_" + action,
                projectKey,
                envKey,
                "SEGMENT",
                segment.key(),
                segment.version(),
                Instant.now(),
                segment
        );
        broadcastEvent(projectKey, envKey, "segment_update", message);
    }

    public void broadcastSnapshot(String projectKey, String envKey, EnvironmentSnapshotResponse snapshot) {
        SseStreamMessage message = new SseStreamMessage(
                "SNAPSHOT_UPDATE",
                projectKey,
                envKey,
                "ENVIRONMENT",
                envKey,
                snapshot.revision(),
                Instant.now(),
                snapshot
        );
        broadcastEvent(projectKey, envKey, "snapshot", message);
    }

    public void broadcastEvent(String projectKey, String envKey, String eventName, Object data) {
        String key = buildKey(projectKey, envKey);
        List<SseEmitter> list = emittersByEnv.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }

        virtualExecutor.submit(() -> {
            for (SseEmitter emitter : list) {
                virtualExecutor.submit(() -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name(eventName)
                                .data(data)
                        );
                    } catch (Exception ex) {
                        removeEmitter(key, emitter);
                    }
                });
            }
        });
    }

    @Scheduled(fixedDelay = 20000)
    public void sendHeartbeat() {
        if (emittersByEnv.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<SseEmitter>> entry : emittersByEnv.entrySet()) {
            String key = entry.getKey();
            List<SseEmitter> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;

            for (SseEmitter emitter : list) {
                virtualExecutor.submit(() -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .comment("keepalive")
                        );
                    } catch (Exception ex) {
                        removeEmitter(key, emitter);
                    }
                });
            }
        }
    }

    public int getActiveSubscriberCount(String projectKey, String envKey) {
        List<SseEmitter> list = emittersByEnv.get(buildKey(projectKey, envKey));
        return list != null ? list.size() : 0;
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> list = emittersByEnv.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByEnv.remove(key, list);
            }
        }
    }

    private String buildKey(String projectKey, String envKey) {
        return projectKey + ":" + envKey;
    }
}
