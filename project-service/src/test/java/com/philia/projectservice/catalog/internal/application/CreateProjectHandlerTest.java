package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.CreateProjectCommand;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.api.ProjectMediaUpload;
import com.philia.projectservice.catalog.internal.application.exception.MediaStorageUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.OwnerProfileUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectMediaValidationException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.OwnerSnapshotRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaStorage;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagRepository;
import com.philia.projectservice.catalog.internal.domain.Project;
import com.philia.projectservice.catalog.internal.domain.ProjectMediaType;
import com.philia.projectservice.catalog.internal.domain.ProjectSlug;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final String OWNER_AVATAR_URL = "https://cdn.example.com/avatars/philia.webp";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

    private final FakeProjectRepository projects = new FakeProjectRepository();
    private final FakeProjectTagRepository projectTags = new FakeProjectTagRepository();
    private final FakeProjectMediaRepository projectMedia = new FakeProjectMediaRepository();
    private final FakeMediaStorage storage = new FakeMediaStorage();
    private final FakeOwnerSnapshotRepository ownerSnapshots = new FakeOwnerSnapshotRepository();

    @Test
    void createsDraftProjectAndAssignsValidatedTags() {
        var result = handler().create(command());

        assertThat(result.id()).isNotNull();
        assertThat(result.owner().id()).isEqualTo(OWNER_ID);
        assertThat(result.owner().displayName()).isEqualTo("Philia");
        assertThat(result.owner().avatarUrl()).isEqualTo(OWNER_AVATAR_URL);
        assertThat(result.category().id()).isEqualTo(CATEGORY_ID);
        assertThat(result.subCategory().id()).isEqualTo(SUB_CATEGORY_ID);
        assertThat(result.slug()).isEqualTo("fiurozz-backend");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.visibility()).isEqualTo("PRIVATE");
        assertThat(result.sourceVisibility()).isEqualTo("HIDDEN");
        assertThat(result.githubUrl()).isEqualTo("https://github.com/fizzisme/fiurozz-be");
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
    void uploadsMediaUnderProjectPrefixAndUsesFirstImageAsThumbnail() {
        var result = handler().create(command());

        assertThat(storage.stored).hasSize(4);
        assertThat(storage.stored).allSatisfy(key -> assertThat(key).startsWith("projects/" + result.id() + "/"));
        assertThat(storage.stored).extracting(key -> key.substring(key.lastIndexOf('.') + 1))
                .containsExactly("png", "jpg", "png", "mp4");

        assertThat(projectMedia.projectId).isEqualTo(result.id());
        assertThat(projectMedia.createdAt).isEqualTo(NOW);
        assertThat(projectMedia.media).extracting(ProjectMediaRepository.NewProjectMedia::type)
                .containsExactly(ProjectMediaType.IMAGE, ProjectMediaType.IMAGE, ProjectMediaType.IMAGE,
                        ProjectMediaType.VIDEO);
        assertThat(projectMedia.media).extracting(ProjectMediaRepository.NewProjectMedia::objectKey)
                .containsExactlyElementsOf(storage.stored);

        var firstImageUrl = projectMedia.media.getFirst().url();
        assertThat(firstImageUrl).isEqualTo("https://cdn.example.com/" + storage.stored.getFirst());
        assertThat(projects.saved.thumbnailUrl()).isEqualTo(firstImageUrl);
        assertThat(result.thumbnailUrl()).isEqualTo(firstImageUrl);
        assertThat(result.images()).hasSize(3).first().isEqualTo(firstImageUrl);
        assertThat(result.media()).extracting(ProjectDetailResult.Media::mediaType)
                .containsExactly("IMAGE", "IMAGE", "IMAGE", "VIDEO");
        assertThat(result.media()).extracting(ProjectDetailResult.Media::sortOrder).containsExactly(0, 1, 2, 3);
        assertThat(result.media().getLast().url()).isEqualTo(projectMedia.media.getLast().url());
    }

    @Test
    void rejectsDuplicateOwnerSlugBeforeUploading() {
        projects.slugExists = true;

        assertThatThrownBy(() -> handler().create(command()))
                .isInstanceOf(ProjectSlugAlreadyExistsException.class)
                .hasMessageContaining("fiurozz-backend");

        assertThat(storage.stored).isEmpty();
        assertThat(projects.saved).isNull();
        assertThat(projectTags.assignments).isEmpty();
    }

    @Test
    void usesTheOwnerSnapshotInsteadOfTheGatewayHeaders() {
        ownerSnapshots.snapshot = new OwnerSnapshotRepository.OwnerSnapshot(OWNER_ID, "Snapshot Name", null);

        var result = handler().create(command());

        assertThat(result.owner().displayName()).isEqualTo("Snapshot Name");
        assertThat(result.owner().avatarUrl()).isNull();
        assertThat(projects.saved.ownerDisplayName()).isEqualTo("Snapshot Name");
    }

    @Test
    void rejectsCreationBeforeUploadingWhenTheOwnerSnapshotIsMissing() {
        ownerSnapshots.snapshot = null;

        assertThatThrownBy(() -> handler().create(command()))
                .isInstanceOf(OwnerProfileUnavailableException.class)
                .hasMessageContaining(OWNER_ID.toString());

        assertThat(storage.stored).isEmpty();
        assertThat(projects.saved).isNull();
    }

    @Test
    void rejectsCreationWhenTheOwnerSnapshotHasNoDisplayName() {
        ownerSnapshots.snapshot = new OwnerSnapshotRepository.OwnerSnapshot(OWNER_ID, null, OWNER_AVATAR_URL);

        assertThatThrownBy(() -> handler().create(command()))
                .isInstanceOf(OwnerProfileUnavailableException.class);

        assertThat(storage.stored).isEmpty();
    }

    @Test
    void rejectsInvalidMediaBeforeUploading() {
        var command = command(List.of(image(PNG), image(PNG)), null);

        assertThatThrownBy(() -> handler().create(command))
                .isInstanceOf(ProjectMediaValidationException.class);

        assertThat(storage.stored).isEmpty();
        assertThat(projects.saved).isNull();
    }

    @Test
    void rejectsInvalidProjectFieldsBeforeUploading() {
        var command = new CreateProjectCommand(
                SUB_CATEGORY_ID, "Fiurozz Backend", "x".repeat(501), "The complete project description",
                null, null, null, List.of(), List.of(), List.of(), threeImages(), null
        );

        assertThatThrownBy(() -> handler().create(command))
                .isInstanceOf(InvalidProjectException.class);

        assertThat(storage.stored).isEmpty();
    }

    @Test
    void deletesAlreadyUploadedMediaWhenAnUploadFails() {
        storage.failOnPut = 3;

        assertThatThrownBy(() -> handler().create(command()))
                .isInstanceOf(MediaStorageUnavailableException.class);

        assertThat(storage.deleted).containsExactlyElementsOf(storage.stored);
        assertThat(storage.stored).hasSize(2);
        assertThat(projects.saved).isNull();
    }

    @Test
    void deletesUploadedMediaWhenPersistingFails() {
        projectMedia.failure = new IllegalStateException("database unavailable");

        assertThatThrownBy(() -> handler().create(command()))
                .isSameAs(projectMedia.failure);

        assertThat(storage.stored).hasSize(4);
        assertThat(storage.deleted).containsExactlyElementsOf(storage.stored);
    }

    @Test
    void keepsOriginalErrorWhenCleanupAlsoFails() {
        projectMedia.failure = new IllegalStateException("database unavailable");
        storage.failOnDelete = true;

        assertThatThrownBy(() -> handler().create(command()))
                .isSameAs(projectMedia.failure)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .singleElement().isInstanceOf(MediaStorageUnavailableException.class));
    }

    private CreateProjectHandler handler() {
        // The gateway headers deliberately carry a different name than the snapshot.
        CurrentActor currentActor = () -> Optional.of(new CurrentActor.Actor(OWNER_ID, "owner@example.com", null));
        return new CreateProjectHandler(
                currentActor,
                ownerSnapshots,
                projects,
                new FakeCatalogReferenceQuery(),
                storage,
                new CreateProjectWriter(projects, projectTags, projectMedia),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static CreateProjectCommand command() {
        return command(List.of(image(PNG), image(JPEG), image(PNG)), new ProjectMediaUpload(
                "demo.mp4", MP4.length, () -> new ByteArrayInputStream(MP4)));
    }

    private static CreateProjectCommand command(List<ProjectMediaUpload> images, ProjectMediaUpload video) {
        return new CreateProjectCommand(
                SUB_CATEGORY_ID,
                "Fiurozz Backend",
                "A project catalog backend",
                "The complete project description",
                "https://demo.example.com",
                "https://github.com/fizzisme/fiurozz-be",
                null,
                List.of("Java", "Spring Boot", "JAVA"),
                List.of("Project Catalog", "project catalog"),
                List.of(TAG_ID, TAG_ID),
                images,
                video
        );
    }

    private static List<ProjectMediaUpload> threeImages() {
        return List.of(image(PNG), image(PNG), image(PNG));
    }

    private static ProjectMediaUpload image(byte[] content) {
        return new ProjectMediaUpload("image", content.length, () -> new ByteArrayInputStream(content));
    }

    private static final class FakeOwnerSnapshotRepository implements OwnerSnapshotRepository {
        OwnerSnapshot snapshot = new OwnerSnapshot(OWNER_ID, "Philia", OWNER_AVATAR_URL);

        @Override
        public Optional<OwnerSnapshot> find(UUID userId) {
            return Optional.ofNullable(snapshot).filter(found -> found.userId().equals(userId));
        }

        @Override
        public void seed(UUID userId, String displayName, String avatarUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void changeDisplayName(UUID userId, String displayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void changeAvatarUrl(UUID userId, String avatarUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void propagateToProjects(UUID userId) {
            throw new UnsupportedOperationException();
        }
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

    private static final class FakeProjectMediaRepository implements ProjectMediaRepository {

        private UUID projectId;
        private List<NewProjectMedia> media = List.of();
        private Instant createdAt;
        private RuntimeException failure;

        @Override
        public void addAll(UUID projectId, List<NewProjectMedia> media, Instant createdAt) {
            if (failure != null) {
                throw failure;
            }
            this.projectId = projectId;
            this.media = media;
            this.createdAt = createdAt;
        }
    }

    private static final class FakeMediaStorage implements ProjectMediaStorage {

        private final List<String> stored = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        /** 1-based index of the put call that fails; 0 disables the failure. */
        private int failOnPut;
        private boolean failOnDelete;

        @Override
        public void put(String objectKey, String contentType, long sizeBytes, InputStream content) {
            if (stored.size() + 1 == failOnPut) {
                throw new MediaStorageUnavailableException("storage down", null);
            }
            stored.add(objectKey);
        }

        @Override
        public void deleteAll(Collection<String> objectKeys) {
            if (failOnDelete) {
                throw new MediaStorageUnavailableException("storage down", null);
            }
            deleted.addAll(objectKeys);
        }

        @Override
        public String publicUrl(String objectKey) {
            return "https://cdn.example.com/" + objectKey;
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
