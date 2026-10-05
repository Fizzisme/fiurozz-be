package com.philia.projectservice.files.internal.adapter.config;

import com.philia.projectservice.files.internal.adapter.out.s3.FileStorageProperties;
import com.philia.projectservice.files.internal.application.*;
import com.philia.projectservice.files.internal.application.port.out.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.time.Duration;

/** Expiry records run-end checkpoints even when the AI process has crashed. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class FileMaintenanceJobs {
    private static final Logger log = LoggerFactory.getLogger(FileMaintenanceJobs.class);
    private final FileWorkspaceStore store;
    private final ProjectFilesHandler handler;
    private final FileBlobStorage blobs;
    private final FileBlobGarbageCollector gc;
    private final FileStorageProperties properties;
    private final Clock clock;

    public FileMaintenanceJobs(FileWorkspaceStore store, ProjectFilesHandler handler, FileBlobStorage blobs,
                               FileBlobGarbageCollector gc, FileStorageProperties properties, Clock clock) {
        this.store = store; this.handler = handler; this.blobs = blobs; this.gc = gc;
        this.properties = properties; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${storage.files.lease-sweep-ms:30000}", initialDelayString = "${storage.files.lease-sweep-ms:30000}")
    public void expireLeases() {
        for (var projectId : store.expiredLeaseProjects(clock.instant())) {
            try { handler.expireLease(projectId); }
            catch (RuntimeException exception) { log.warn("Could not expire project file lease {}", projectId, exception); }
        }
    }

    @Scheduled(cron = "${storage.files.gc-cron:0 0 3 * * *}", zone = "UTC")
    public void collectBlobs() {
        if (!properties.gcEnabled()) return;
        if (properties.gcGraceHours() < 24) {
            log.error("Project file GC requires a grace period of at least 24 hours.");
            return;
        }
        try {
            int removed = 0;
            for (var blob : blobs.list()) {
                if (gc.collect(blob, Duration.ofHours(properties.gcGraceHours()))) removed++;
            }
            log.info("Project file GC removed {} unreferenced blobs", removed);
        } catch (RuntimeException exception) { log.error("Project file GC failed; it will retry on the next schedule", exception); }
    }
}
