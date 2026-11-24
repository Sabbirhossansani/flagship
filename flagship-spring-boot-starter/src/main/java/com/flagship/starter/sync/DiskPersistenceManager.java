package com.flagship.starter.sync;

import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.starter.config.FlagshipStarterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Manages local disk caching of the flag snapshot for cold-start and offline resilience (FR6).
 */
public class DiskPersistenceManager {

    private static final Logger log = LoggerFactory.getLogger(DiskPersistenceManager.class);

    private final FlagshipStarterProperties properties;

    public DiskPersistenceManager(FlagshipStarterProperties properties) {
        this.properties = properties;
    }

    public synchronized void persist(EnvironmentSnapshot snapshot) {
        if (!properties.isDiskPersistenceEnabled() || snapshot == null) {
            return;
        }

        try {
            Path targetPath = Path.of(properties.getDiskPersistencePath());
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Path tempFile = Files.createTempFile(parentDir != null ? parentDir : Path.of("."), "flagship-snap", ".tmp");
            String json = FlagshipSnapshotParser.serialize(snapshot);
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);

            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Persisted FlagShip snapshot revision {} to disk at '{}'", snapshot.revision(), targetPath);
        } catch (Exception ex) {
            log.warn("Failed to persist FlagShip snapshot to disk: {}", ex.getMessage());
        }
    }

    public synchronized EnvironmentSnapshot load() {
        if (!properties.isDiskPersistenceEnabled()) {
            return null;
        }

        File file = new File(properties.getDiskPersistencePath());
        if (!file.exists() || file.length() == 0) {
            return null;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            EnvironmentSnapshot snapshot = FlagshipSnapshotParser.parse(json, properties.getProjectKey(), properties.getEnvironmentKey());
            log.info("Loaded cached FlagShip snapshot (rev={}) from disk '{}'", snapshot.revision(), file.getAbsolutePath());
            return snapshot;
        } catch (Exception ex) {
            log.warn("Failed to read FlagShip cache from disk '{}': {}", file.getAbsolutePath(), ex.getMessage());
            return null;
        }
    }
}

