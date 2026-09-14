package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.api.UpdateProjectCommand;
import com.philia.projectservice.catalog.api.UpdateProjectUseCase;
import com.philia.projectservice.catalog.internal.application.exception.ProjectForbiddenException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotEditableException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectStaleVersionException;
import com.philia.projectservice.catalog.internal.application.exception.SubCategoryUnavailableException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectDetailQuery;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectUpdateGateway;
import com.philia.projectservice.catalog.internal.domain.ProjectSlug;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class UpdateProjectHandler implements UpdateProjectUseCase {

    private static final int MAX_TECH_STACK_ITEMS = 20;
    private static final int MAX_FEATURE_ITEMS = 30;

    private final CurrentActor currentActor;
    private final CatalogReferenceQuery catalogReferenceQuery;
    private final ProjectUpdateGateway projectUpdateGateway;
    private final ProjectDetailQuery projectDetailQuery;
    private final Clock clock;

    public UpdateProjectHandler(
            CurrentActor currentActor,
            CatalogReferenceQuery catalogReferenceQuery,
            ProjectUpdateGateway projectUpdateGateway,
            ProjectDetailQuery projectDetailQuery,
            Clock clock
    ) {
        this.currentActor = currentActor;
        this.catalogReferenceQuery = catalogReferenceQuery;
        this.projectUpdateGateway = projectUpdateGateway;
        this.projectDetailQuery = projectDetailQuery;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectDetailResult updateProject(UpdateProjectCommand command) {
        if (command == null || command.projectId() == null || !command.hasChanges()) {
            throw new InvalidProjectException("At least one project field must be supplied.");
        }
        if (command.expectedVersion() < 0) {
            throw new InvalidProjectException("If-Match must contain a non-negative project version.");
        }

        var actor = currentActor.getRequiredActor();
        var current = projectUpdateGateway.findActiveState(command.projectId())
                .orElseThrow(ProjectNotFoundException::new);
        if (!actor.id().equals(current.ownerId())) {
            throw new ProjectForbiddenException();
        }
        if (current.version() != command.expectedVersion()) {
            throw new ProjectStaleVersionException();
        }
        if ("ARCHIVED".equals(current.status())) {
            throw new ProjectNotEditableException();
        }

        var subCategoryId = command.subCategoryId() == null ? current.subCategoryId() : command.subCategoryId();
        if (command.subCategoryId() != null) {
            catalogReferenceQuery.findActiveSubCategory(subCategoryId)
                    .orElseThrow(() -> new SubCategoryUnavailableException(subCategoryId));
        }
        var slug = command.slug() == null ? current.slug() : ProjectSlug.from(command.slug()).value();
        if (!slug.equals(current.slug()) && projectUpdateGateway.slugExistsForAnotherActiveProject(
                actor.id(), slug, command.projectId())) {
            throw new ProjectSlugAlreadyExistsException(slug);
        }

        var update = new ProjectUpdateGateway.UpdateData(
                command.projectId(), actor.id(), command.expectedVersion(), subCategoryId,
                requiredText(command.title(), current.title(), 180, "Project title"),
                slug,
                requiredText(command.shortDescription(), current.shortDescription(), 500, "Project short description"),
                requiredText(command.description(), current.description(), 50_000, "Project description"),
                optionalHttpsUrl(command.demoUrl(), current.demoUrl()),
                command.techStack() == null ? current.techStack()
                        : normalizedItems(command.techStack(), MAX_TECH_STACK_ITEMS, 60, true, "techStack"),
                command.features() == null ? current.features()
                        : normalizedItems(command.features(), MAX_FEATURE_ITEMS, 200, false, "features"),
                clock.instant()
        );
        if (!projectUpdateGateway.updateIfCurrent(update)) {
            throw new ProjectStaleVersionException();
        }
        return projectDetailQuery.findActiveById(command.projectId()).orElseThrow(ProjectNotFoundException::new);
    }

    private static String requiredText(String requested, String current, int maximumLength, String fieldName) {
        if (requested == null) {
            return current;
        }
        var trimmed = requested.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidProjectException(fieldName + " must not be blank");
        }
        if (trimmed.length() > maximumLength) {
            throw new InvalidProjectException(fieldName + " exceeds its maximum length");
        }
        return trimmed;
    }

    private static String optionalHttpsUrl(String requested, String current) {
        if (requested == null) {
            return current;
        }
        var trimmed = requested.trim();
        if (trimmed.length() > 500 || !trimmed.matches("^https://\\S+$")) {
            throw new InvalidProjectException("Project demo URL must be a valid HTTPS URL");
        }
        return trimmed;
    }

    private static List<String> normalizedItems(
            List<String> values, int maximumItems, int maximumLength, boolean lowercase, String fieldName
    ) {
        if (values.size() > maximumItems) {
            throw new InvalidProjectException(fieldName + " contains too many items");
        }
        var unique = new LinkedHashMap<String, String>();
        for (var value : values) {
            if (value == null || value.isBlank()) {
                throw new InvalidProjectException(fieldName + " must not contain blank items");
            }
            var trimmed = value.trim();
            if (trimmed.length() > maximumLength) {
                throw new InvalidProjectException(fieldName + " item exceeds its maximum length");
            }
            var normalized = trimmed.toLowerCase(Locale.ROOT);
            if (lowercase) {
                normalized = normalized.replaceAll("[\\s_]+", "-").replaceAll("-+", "-");
            }
            unique.putIfAbsent(normalized, lowercase ? normalized : trimmed);
        }
        return new ArrayList<>(unique.values());
    }
}
