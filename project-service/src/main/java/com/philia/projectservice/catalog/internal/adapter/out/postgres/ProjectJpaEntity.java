package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "owner_display_name")
    private String ownerDisplayName;

    @Column(name = "owner_avatar_url")
    private String ownerAvatarUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_category_id", nullable = false)
    private ProjectSubCategoryJpaEntity subCategory;

    private String title;
    private String slug;

    @Column(name = "short_description")
    private String shortDescription;

    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "demo_url")
    private String demoUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tech_stack", columnDefinition = "jsonb")
    private List<String> techStack = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private List<String> features = new ArrayList<>();

    private String status;
    private String visibility;

    @Column(name = "source_visibility")
    private String sourceVisibility;

    @Column(name = "view_count")
    private long viewCount;

    @Column(name = "like_count")
    private long likeCount;

    @Column(name = "comment_count")
    private long commentCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "row_version")
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "project_tags",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<ProjectTagJpaEntity> tags = new LinkedHashSet<>();

    protected ProjectJpaEntity() {
    }

    public static ProjectJpaEntity forCreate(
            Project project,
            ProjectSubCategoryJpaEntity subCategory
    ) {
        var entity = new ProjectJpaEntity();
        entity.id = project.id();
        entity.ownerId = project.ownerId();
        entity.ownerDisplayName = project.ownerDisplayName();
        entity.ownerAvatarUrl = project.ownerAvatarUrl();
        entity.subCategory = subCategory;
        entity.title = project.title();
        entity.slug = project.slug().value();
        entity.shortDescription = project.shortDescription();
        entity.description = project.description();
        entity.thumbnailUrl = project.thumbnailUrl();
        entity.demoUrl = project.demoUrl();
        entity.techStack = new ArrayList<>(project.techStack());
        entity.features = new ArrayList<>(project.features());
        entity.status = project.status().name();
        entity.visibility = project.visibility().name();
        entity.sourceVisibility = project.sourceVisibility().name();
        entity.viewCount = project.viewCount();
        entity.likeCount = project.likeCount();
        entity.commentCount = project.commentCount();
        entity.publishedAt = project.publishedAt();
        entity.deletedAt = project.deletedAt();
        entity.createdAt = project.createdAt();
        entity.updatedAt = project.updatedAt();
        entity.version = project.version();
        return entity;
    }

    public void addTag(ProjectTagJpaEntity tag) {
        tags.add(tag);
    }

    public void replaceTags(Collection<ProjectTagJpaEntity> replacementTags) {
        tags.clear();
        tags.addAll(replacementTags);
    }

    public void updateOwnerFields(
            ProjectSubCategoryJpaEntity subCategory,
            String title,
            String slug,
            String shortDescription,
            String description,
            String demoUrl,
            List<String> techStack,
            List<String> features,
            Instant updatedAt
    ) {
        this.subCategory = subCategory;
        this.title = title;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.description = description;
        this.demoUrl = demoUrl;
        this.techStack = new ArrayList<>(techStack);
        this.features = new ArrayList<>(features);
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getOwnerDisplayName() { return ownerDisplayName; }
    public String getOwnerAvatarUrl() { return ownerAvatarUrl; }
    public ProjectSubCategoryJpaEntity getSubCategory() { return subCategory; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDemoUrl() { return demoUrl; }
    public List<String> getTechStack() { return List.copyOf(techStack); }
    public List<String> getFeatures() { return List.copyOf(features); }
    public String getStatus() { return status; }
    public String getVisibility() { return visibility; }
    public String getSourceVisibility() { return sourceVisibility; }
    public long getViewCount() { return viewCount; }
    public long getLikeCount() { return likeCount; }
    public long getCommentCount() { return commentCount; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public Set<ProjectTagJpaEntity> getTags() { return tags; }
}
