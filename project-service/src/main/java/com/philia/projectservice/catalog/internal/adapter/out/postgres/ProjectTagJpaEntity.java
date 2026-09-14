package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tags")
public class ProjectTagJpaEntity {

    @Id
    private UUID id;

    private String slug;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "normalized_name")
    private String normalizedName;

    private String status;

    protected ProjectTagJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getStatus() {
        return status;
    }
}
