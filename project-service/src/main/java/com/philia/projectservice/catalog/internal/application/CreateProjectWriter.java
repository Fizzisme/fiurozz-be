package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagRepository;
import com.philia.projectservice.catalog.internal.domain.Project;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persists a new project in one transaction. Kept separate from {@link CreateProjectHandler} so the
 * transaction starts only after the media upload, instead of holding a connection during it.
 */
@Component
class CreateProjectWriter {

    private final ProjectRepository projectRepository;
    private final ProjectTagRepository projectTagRepository;
    private final ProjectMediaRepository projectMediaRepository;

    CreateProjectWriter(
            ProjectRepository projectRepository,
            ProjectTagRepository projectTagRepository,
            ProjectMediaRepository projectMediaRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectTagRepository = projectTagRepository;
        this.projectMediaRepository = projectMediaRepository;
    }

    @Transactional
    public void write(Project project, Set<UUID> tagIds, List<ProjectMediaRepository.NewProjectMedia> media) {
        projectRepository.save(project);
        projectTagRepository.addAll(project.id(), tagIds);
        projectMediaRepository.addAll(project.id(), media, project.createdAt());
    }
}
