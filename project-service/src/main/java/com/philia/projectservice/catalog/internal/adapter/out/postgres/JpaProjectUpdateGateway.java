package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.ProjectUpdateGateway;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaProjectUpdateGateway implements ProjectUpdateGateway {

    private final JpaProjectCommandRepository projectRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public JpaProjectUpdateGateway(
            JpaProjectCommandRepository projectRepository,
            jakarta.persistence.EntityManager entityManager
    ) {
        this.projectRepository = projectRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ProjectState> findActiveState(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId).map(project -> new ProjectState(
                project.getOwnerId(), project.getStatus(), project.getVersion(), project.getSubCategory().getId(),
                project.getTitle(), project.getSlug(), project.getShortDescription(), project.getDescription(),
                project.getDemoUrl(), project.getTechStack(), project.getFeatures()
        ));
    }

    @Override
    public boolean slugExistsForAnotherActiveProject(UUID ownerId, String slug, UUID projectId) {
        return projectRepository.existsByOwnerIdAndSlugAndDeletedAtIsNullAndIdNot(ownerId, slug, projectId);
    }

    @Override
    public boolean updateIfCurrent(UpdateData update) {
        // Advance row_version first with the ETag in the WHERE clause. This makes
        // the update conditional without Hibernate changing versions for tag rows.
        if (projectRepository.advanceVersionIfCurrent(
                update.projectId(), update.ownerId(), update.expectedVersion(), update.updatedAt()) != 1) {
            return false;
        }
        var project = projectRepository.findByIdAndDeletedAtIsNull(update.projectId()).orElse(null);
        if (project == null) {
            return false;
        }
        project.updateOwnerFields(
                entityManager.getReference(ProjectSubCategoryJpaEntity.class, update.subCategoryId()),
                update.title(), update.slug(), update.shortDescription(), update.description(), update.demoUrl(),
                update.techStack(), update.features(), update.updatedAt()
        );
        projectRepository.saveAndFlush(project);
        return true;
    }
}
