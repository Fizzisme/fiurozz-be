package com.philia.projectservice.catalog.api;

import java.util.UUID;

/** Public catalog boundary: files never access catalog's internal entities directly. */
public interface ProjectFilesAccess {
    ProjectAccess findProject(UUID projectId, boolean lockForWrite);
    /** Internal maintenance may finish expired leases on soft-deleted projects as well. */
    void lockForMaintenance(UUID projectId);

    record ProjectAccess(UUID id, UUID ownerId, String status, String visibility,
                         String sourceVisibility, long version) {
        public boolean exposesSource() {
            return readableByNonOwner() && "PUBLIC".equals(sourceVisibility);
        }
        public boolean readableByNonOwner() {
            return "PUBLISHED".equals(status) && ("PUBLIC".equals(visibility) || "UNLISTED".equals(visibility));
        }
    }
}
