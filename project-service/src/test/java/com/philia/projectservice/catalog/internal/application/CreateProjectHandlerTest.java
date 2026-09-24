package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.CreateProjectCommand;
import com.philia.projectservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagRepository;
import com.philia.projectservice.catalog.internal.domain.Project;
import com.philia.projectservice.catalog.internal.domain.ProjectSlug;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateProjectHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-24T02:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SUB_CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TAG_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void createsDraftProjectAndAssignsValidatedTags() {
        var projects = new FakeProjectRepository();
        var projectTags = new FakeProjectTagRepository();
        var handler = handler(projects, projectTags);

        var result = handler.create(command());

        assertThat(result.id()).isNotNull();
        assertThat(result.owner().id()).isEqualTo(OWNER_ID);
        assertThat(result.category().id()).isEqualTo(CATEGORY_ID);
        assertThat(result.subCategory().id()).isEqualTo(SUB_CATEGORY_ID);
        assertThat(result.slug()).isEqualTo("fiurozz-backend");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.visibility()).isEqualTo("PRIVATE");
        assertThat(result.sourceVisibility()).isEqualTo("HIDDEN");
        assertThat(result.techStack()).containsExactly("java", "spring-boot");
        assertThat(result.features()).containsExactly("Project Catalog");
        assertThat(result.tags()).singleElement().satisfies(tag -> assertThat(tag.id()).isEqualTo(TAG_ID));
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.version()).isZero();

        assertThat(projects.saved).isNotNull();
        assertThat(projects.saved.id()).isEqualTo(result.id());
        assertThat(projectTags.assignments).containsExactly(TAG_ID);
    }

    @Test
    void rejectsDuplicateOwnerSlugBeforeSaving() {
        var projects = new FakeProjectRepository();
        projects.slugExists = true;
        var projectTags = new FakeProjectTagRepository();
        var handler = handler(projects, projectTags);

        assertThatThrownBy(() -> handler.create(command()))
                .isInstanceOf(ProjectSlugAlreadyExistsException.class)
                .hasMessageContaining("fiurozz-backend");

        assertThat(projects.saved).isNull();
        assertThat(projectTags.assignments).isEmpty();
    }

    private static CreateProjectHandler handler(
            FakeProjectRepository projects,
            FakeProjectTagRepository projectTags
    ) {
        CurrentActor currentActor = () -> Optional.of(new CurrentActor.Actor(OWNER_ID, "Philia", null));
        return new CreateProjectHandler(
                currentActor,
                projects,
                projectTags,
                new FakeCatalogReferenceQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static CreateProjectCommand command() {
        return new CreateProjectCommand(
                SUB_CATEGORY_ID,
                "Fiurozz Backend",
                "A project catalog backend",
                "The complete project description",
                "https://demo.example.com",
                null,
                List.of("Java", "Spring Boot", "JAVA"),
                List.of("Project Catalog", "project catalog"),
                List.of(TAG_ID, TAG_ID)
        );
    }

    private static final class FakeProjectRepository implements ProjectRepository {

        private boolean slugExists;
        private Project saved;

        @Override
        public boolean existsActiveSlug(UUID ownerId, ProjectSlug slug) {
            return slugExists;
        }

        @Override
        public void save(Project project) {
            saved = project;
        }
    }

    private static final class FakeProjectTagRepository implements ProjectTagRepository {

        private final List<UUID> assignments = new ArrayList<>();

        @Override
        public void addAll(UUID projectId, Set<UUID> tagIds) {
            assignments.addAll(tagIds);
        }
    }

    private static final class FakeCatalogReferenceQuery implements CatalogReferenceQuery {

        @Override
        public Optional<SubCategoryReference> findActiveSubCategory(UUID subCategoryId) {
            if (!SUB_CATEGORY_ID.equals(subCategoryId)) {
                return Optional.empty();
            }
            return Optional.of(new SubCategoryReference(
                    SUB_CATEGORY_ID,
                    "backend-development",
                    "backend-development",
                    "Backend Development",
                    new CategoryReference(
                            CATEGORY_ID,
                            "software-development",
                            "software-development",
                            "Software Development",
                            "code"
                    )
            ));
        }

        @Override
        public List<TagReference> findActiveTags(Set<UUID> tagIds) {
            return tagIds.contains(TAG_ID)
                    ? List.of(new TagReference(TAG_ID, "backend", "Backend"))
                    : List.of();
        }
    }
}
