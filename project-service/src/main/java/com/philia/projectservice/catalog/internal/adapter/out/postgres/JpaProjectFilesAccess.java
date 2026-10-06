package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.api.ProjectFilesAccess;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class JpaProjectFilesAccess implements ProjectFilesAccess {
    private final JpaProjectCommandRepository repository;

    public JpaProjectFilesAccess(JpaProjectCommandRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProjectAccess findProject(UUID projectId, boolean lockForWrite) {
        var project = (lockForWrite ? repository.findFilesProjectForUpdate(projectId)
                : repository.findByIdAndDeletedAtIsNull(projectId)).orElseThrow(ProjectNotFoundException::new);
        return new ProjectAccess(project.getId(), project.getOwnerId(), project.getStatus(),
                project.getVisibility(), project.getSourceVisibility(), project.getVersion());
    }

    @Override
    public void lockForMaintenance(UUID projectId) {
        repository.findAnyFilesProjectForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
    }
}
