package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.api.PublishProjectCommand;
import com.philia.projectservice.catalog.api.PublishProjectUseCase;
import com.philia.projectservice.catalog.internal.application.exception.ProjectForbiddenException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectInvalidStateException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectStaleVersionException;
import com.philia.projectservice.catalog.internal.application.exception.SubCategoryUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.TagsUnavailableException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectDetailQuery;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectPublicationGateway;
import com.philia.projectservice.catalog.internal.domain.ProjectSlug;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;

/**
 * Publishes an owned DRAFT project after validating that its catalog references
 * are still available.
 */
@Service
public class PublishProjectHandler implements PublishProjectUseCase {

    private final CurrentActor currentActor;
    private final CatalogReferenceQuery catalogReferenceQuery;
    private final ProjectPublicationGateway projectPublicationGateway;
    private final ProjectDetailQuery projectDetailQuery;
    private final Clock clock;

    public PublishProjectHandler(
            CurrentActor currentActor,
            CatalogReferenceQuery catalogReferenceQuery,
            ProjectPublicationGateway projectPublicationGateway,
            ProjectDetailQuery projectDetailQuery,
            Clock clock
    ) {
        this.currentActor = currentActor;
        this.catalogReferenceQuery = catalogReferenceQuery;
        this.projectPublicationGateway = projectPublicationGateway;
        this.projectDetailQuery = projectDetailQuery;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectDetailResult publishProject(PublishProjectCommand command) {
        if (command == null || command.projectId() == null) {
            throw new InvalidProjectException("Project ID is required.");
        }
        if (command.expectedVersion() < 0) {
            throw new InvalidProjectException("If-Match must contain a non-negative project version.");
        }

        var actor = currentActor.getRequiredActor();
        var project = projectPublicationGateway.findActiveState(command.projectId())
                .orElseThrow(ProjectNotFoundException::new);
        if (!actor.id().equals(project.ownerId())) {
            throw new ProjectForbiddenException();
        }
        if (project.version() != command.expectedVersion()) {
            throw new ProjectStaleVersionException();
        }
        if ("PUBLISHED".equals(project.status())) {
            return projectDetailQuery.findActiveById(command.projectId()).orElseThrow(ProjectNotFoundException::new);
        }
        if (!"DRAFT".equals(project.status())) {
            throw new ProjectInvalidStateException("Only DRAFT projects can be published.");
        }

        validateProjectContent(project);
        validateCatalogReferences(project);
        if (!projectPublicationGateway.publishIfCurrent(
                command.projectId(), actor.id(), command.expectedVersion(), clock.instant())) {
            throw new ProjectStaleVersionException();
        }
        return projectDetailQuery.findActiveById(command.projectId()).orElseThrow(ProjectNotFoundException::new);
    }

    private static void validateProjectContent(ProjectPublicationGateway.ProjectState project) {
        requiredText(project.title(), 180, "Project title");
        ProjectSlug.from(project.slug());
        requiredText(project.shortDescription(), 500, "Project short description");
        requiredText(project.description(), 50_000, "Project description");

        if (project.demoUrl() != null) {
            var demoUrl = project.demoUrl().trim();
            if (demoUrl.length() > 500 || !demoUrl.matches("^https://\\S+$")) {
                throw new InvalidProjectException("Project demo URL must be a valid HTTPS URL");
            }
        }
    }

    private static void requiredText(String value, int maximumLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidProjectException(fieldName + " must not be blank");
        }
        if (value.trim().length() > maximumLength) {
            throw new InvalidProjectException(fieldName + " exceeds its maximum length");
        }
    }

    private void validateCatalogReferences(ProjectPublicationGateway.ProjectState project) {
        catalogReferenceQuery.findActiveSubCategory(project.subCategoryId())
                .orElseThrow(() -> new SubCategoryUnavailableException(project.subCategoryId()));

        if (project.tagIds().isEmpty()) {
            return;
        }
        var activeTags = catalogReferenceQuery.findActiveTags(project.tagIds());
        var activeIds = new LinkedHashSet<java.util.UUID>();
        activeTags.forEach(tag -> activeIds.add(tag.id()));
        var unavailableIds = new LinkedHashSet<>(project.tagIds());
        unavailableIds.removeAll(activeIds);
        if (!unavailableIds.isEmpty()) {
            throw new TagsUnavailableException(unavailableIds);
        }
    }
}
