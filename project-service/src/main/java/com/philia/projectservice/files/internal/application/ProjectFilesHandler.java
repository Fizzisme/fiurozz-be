package com.philia.projectservice.files.internal.application;

import com.philia.projectservice.catalog.api.ProjectFilesAccess;
import com.philia.projectservice.catalog.api.ProjectPublishedEvent;
import com.philia.projectservice.files.api.*;
import com.philia.projectservice.files.internal.application.port.out.*;
import com.philia.projectservice.files.internal.domain.*;
import com.philia.projectservice.files.internal.domain.exception.FilesException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

/** Coordinates catalog authorization, immutable snapshots and private blob storage. */
@Service
@Transactional
public class ProjectFilesHandler implements ProjectFilesUseCase {
    private final ProjectFilesAccess projects;
    private final FilesActor actor;
    private final FileWorkspaceStore store;
    private final FileBlobStorage blobs;
    private final Clock clock;

    public ProjectFilesHandler(ProjectFilesAccess projects, FilesActor actor, FileWorkspaceStore store,
                               FileBlobStorage blobs, Clock clock) {
        this.projects = projects; this.actor = actor; this.store = store; this.blobs = blobs; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public FilesResults.Tree getFiles(UUID projectId, Long revision, boolean includeContent) {
        var userId = actor.requiredUserId();
        var project = projects.findProject(projectId, false);
        var workspace = store.load(projectId);
        long selected;
        if (project.ownerId().equals(userId)) {
            selected = revision == null ? workspace.revision : revision;
        } else {
            // A public catalog project does not automatically expose its source or working copy.
            if (!project.exposesSource() || workspace.publishedRevision == null) notFound();
            selected = workspace.publishedRevision;
            if (revision != null && revision != selected) {
                throw new FilesException(404, "REVISION_NOT_FOUND", "The files revision does not exist.");
            }
        }
        if (selected < 0) FilePolicy.invalid("Revision must be non-negative.");
        return new FilesResults.Tree(selected, fileResults(store.snapshot(projectId, selected).files(), includeContent));
    }

    @Override
    public FilesResults.Saved applyChanges(ApplyFileChangesCommand command) {
        var userId = actor.requiredUserId();
        var workspace = ownedWorkspace(command.projectId(), userId, true);
        workspace.requireWrite(command.expectedRevision(), command.leaseId(), userId);
        FilePolicy.label(command.label());
        FilePolicy.source(command.source());
        if (command.changes() == null || command.changes().isEmpty()) FilePolicy.invalid("At least one change is required.");
        if (command.changes().size() > FilePolicy.MAX_CHANGES) FilePolicy.limit();

        var tree = new TreeMap<>(store.snapshot(command.projectId(), workspace.revision).files());
        var uploads = new TreeMap<String, byte[]>();
        var changed = new TreeSet<String>();
        // Validate the entire batch before uploading anything. Duplicate paths are ambiguous.
        for (var change : command.changes()) {
            if (change == null) FilePolicy.invalid("A change cannot be null.");
            FilePolicy.path(change.path());
            if (!changed.add(change.path())) FilePolicy.invalid("Each path may appear only once in a batch.");
            switch (change.op() == null ? "" : change.op()) {
                case "PUT" -> {
                    var bytes = FilePolicy.text(change.content());
                    var hash = FilePolicy.sha256(bytes);
                    tree.put(change.path(), new FilesResults.Metadata(hash, bytes.length));
                    uploads.put(hash, bytes);
                }
                case "DELETE" -> {
                    if (change.content() != null) FilePolicy.invalid("DELETE must not include content.");
                    if (tree.remove(change.path()) == null) throw new FilesException(404, "FILE_NOT_FOUND", "The file does not exist.");
                }
                default -> FilePolicy.invalid("Change op must be PUT or DELETE.");
            }
        }
        FilePolicy.tree(tree);
        // GC takes the same per-hash database lock before deletion. Locks live through commit.
        store.lockBlobs(new ArrayList<>(uploads.keySet()));
        uploads.forEach(blobs::putIfAbsent);
        workspace.revision++;
        store.saveSnapshot(workspace.projectId, workspace.revision, tree, command.label(),
                command.source(), changed.size(), clock.instant());
        store.save(workspace);
        var manifest = new ArrayList<FilesResults.File>();
        // Deleted paths have no hash in the changed-path response.
        for (var path : changed) {
            var metadata = tree.get(path);
            manifest.add(new FilesResults.File(path, metadata == null ? 0 : metadata.size(),
                    metadata == null ? null : metadata.sha256(), null, metadata == null ? true : null));
        }
        return new FilesResults.Saved(workspace.revision, List.copyOf(manifest));
    }

    @Override
    @Transactional(readOnly = true)
    public FilesResults.Page listRevisions(UUID projectId, int page, int size) {
        requireOwner(projects.findProject(projectId, false), actor.requiredUserId(), false);
        if (page < 0 || size < 1 || size > 100) FilePolicy.invalid("Page must be non-negative and size must be 1..100.");
        return store.revisions(projectId, page, size);
    }

    @Override
    public FilesResults.Reverted revert(RevertFilesCommand command) {
        var userId = actor.requiredUserId();
        var workspace = ownedWorkspace(command.projectId(), userId, true);
        workspace.requireWrite(command.expectedRevision(), command.leaseId(), userId);
        if (command.toRevision() < 0) FilePolicy.invalid("toRevision must be non-negative.");
        FilePolicy.label(command.label());
        var previous = store.snapshot(command.projectId(), workspace.revision).files();
        var target = store.snapshot(command.projectId(), command.toRevision()).files();
        var paths = new HashSet<>(previous.keySet());
        paths.addAll(target.keySet());
        var changed = (int) paths.stream().filter(path -> !Objects.equals(previous.get(path), target.get(path))).count();
        workspace.revision++;
        store.saveSnapshot(workspace.projectId, workspace.revision, target, command.label(),
                new FilesResults.Source("USER", null), changed, clock.instant());
        store.save(workspace);
        return new FilesResults.Reverted(workspace.revision);
    }

    @Override
    public FilesResults.Lease acquireLease(AcquireLeaseCommand command) {
        var userId = actor.requiredUserId();
        var workspace = ownedWorkspace(command.projectId(), userId, true);
        FilePolicy.boundedText(command.holder(), 100, "Holder");
        FilePolicy.boundedText(command.runId(), 200, "runId");
        if (command.ttlSeconds() < 30 || command.ttlSeconds() > 3600) FilePolicy.invalid("Lease TTL must be 30..3600 seconds.");
        if (workspace.hasLease()) throw new FilesException(423, "PROJECT_LEASED",
                "The project is being edited.", null, workspace.leaseStatus());
        workspace.leaseId = UUID.randomUUID(); workspace.leaseOwnerId = userId;
        workspace.holder = command.holder(); workspace.runId = command.runId();
        workspace.ttlSeconds = command.ttlSeconds(); workspace.expiresAt = clock.instant().plusSeconds(command.ttlSeconds());
        workspace.baseRevision = store.startRun(workspace.projectId, command.runId(), workspace.revision);
        ensureRevisionZero(workspace);
        store.save(workspace);
        return workspace.lease();
    }

    @Override
    public FilesResults.Lease renewLease(UUID projectId, UUID leaseId) {
        var userId = actor.requiredUserId();
        var workspace = ownedWorkspace(projectId, userId, true);
        if (!workspace.hasLease() || !workspace.leaseId.equals(leaseId) || !workspace.leaseOwnerId.equals(userId)) {
            throw new FilesException(404, "LEASE_NOT_FOUND", "The lease was released or expired.");
        }
        workspace.expiresAt = clock.instant().plusSeconds(workspace.ttlSeconds);
        store.save(workspace);
        return workspace.lease();
    }

    @Override
    public void releaseLease(UUID projectId, UUID leaseId) {
        var workspace = ownedWorkspace(projectId, actor.requiredUserId(), false);
        if (workspace.hasLease() && workspace.leaseId.equals(leaseId)) {
            store.endRun(projectId, workspace.runId, workspace.revision);
            workspace.clearLease();
            store.save(workspace);
        }
        // Unknown, replaced or expired lease IDs are an idempotent no-op.
    }

    @Override
    public FilesResults.LeaseStatus getLease(UUID projectId) {
        return ownedWorkspace(projectId, actor.requiredUserId(), false).leaseStatus();
    }

    /** Called synchronously by the catalog publication event inside the same transaction. */
    public void pinPublished(ProjectPublishedEvent event) {
        var project = projects.findProject(event.projectId(), true);
        if (!project.ownerId().equals(event.ownerId()) || project.version() != event.expectedVersion()
                || !"PUBLISHED".equals(project.status())) {
            throw new FilesException(412, "PROJECT_STALE_VERSION", "The project changed during publication.");
        }
        var workspace = store.load(event.projectId());
        expire(workspace);
        ensureRevisionZero(workspace);
        workspace.publishedRevision = workspace.revision;
        store.save(workspace);
    }

    /** Scheduled expiry does not require a live user's security context. */
    public void expireLease(UUID projectId) {
        projects.lockForMaintenance(projectId);
        expire(store.load(projectId));
    }

    private FileWorkspace ownedWorkspace(UUID projectId, UUID userId, boolean writing) {
        requireOwner(projects.findProject(projectId, true), userId, writing);
        var workspace = store.load(projectId);
        expire(workspace);
        return workspace;
    }

    private static void requireOwner(ProjectFilesAccess.ProjectAccess project, UUID userId, boolean writing) {
        if (!project.ownerId().equals(userId)) {
            if (!project.readableByNonOwner()) notFound();
            throw new FilesException(403, "PROJECT_FORBIDDEN", "Only the project owner may use this operation.");
        }
        if (writing && !("DRAFT".equals(project.status()) || "PUBLISHED".equals(project.status()))) {
            throw new FilesException(409, "PROJECT_INVALID_STATE", "Archived projects cannot be edited.");
        }
    }

    private void expire(FileWorkspace workspace) {
        if (workspace.hasLease() && !workspace.expiresAt.isAfter(clock.instant())) {
            // Capture the last persisted revision BEFORE a successor is allowed to write.
            store.endRun(workspace.projectId, workspace.runId, workspace.revision);
            workspace.clearLease();
            store.save(workspace);
        }
    }

    private void ensureRevisionZero(FileWorkspace workspace) {
        if (workspace.revision == 0 && store.revisions(workspace.projectId, 0, 1).totalElements() == 0) {
            store.saveSnapshot(workspace.projectId, 0, Map.of(), "Initial empty revision",
                    new FilesResults.Source("USER", null), 0, clock.instant());
        }
    }

    private List<FilesResults.File> fileResults(Map<String, FilesResults.Metadata> manifest, boolean includeContent) {
        return new TreeMap<>(manifest).entrySet().stream().map(entry -> {
            var metadata = entry.getValue();
            String content = null;
            if (includeContent) {
                var bytes = blobs.read(metadata.sha256());
                if (bytes.length != metadata.size() || !FilePolicy.sha256(bytes).equals(metadata.sha256())) {
                    throw new FilesException(503, "FILES_STORAGE_UNAVAILABLE", "Stored file content is unavailable.");
                }
                content = FilePolicy.decode(bytes);
            }
            return new FilesResults.File(entry.getKey(), metadata.size(), metadata.sha256(), content);
        }).toList();
    }

    private static void notFound() { throw new FilesException(404, "PROJECT_NOT_FOUND", "The project does not exist."); }
}
