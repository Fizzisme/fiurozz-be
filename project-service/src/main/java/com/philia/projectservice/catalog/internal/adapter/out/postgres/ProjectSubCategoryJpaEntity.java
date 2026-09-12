package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_sub_categories")
public class ProjectSubCategoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "key")
    private String key;

    private String slug;
    private String title;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProjectCategoryJpaEntity category;

    protected ProjectSubCategoryJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public ProjectCategoryJpaEntity getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
