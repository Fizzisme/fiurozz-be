package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.UUID;

interface JpaProjectSubCategoryBrowseRepository extends Repository<ProjectSubCategoryJpaEntity, UUID> {
    List<ProjectSubCategoryJpaEntity> findByCategory_IdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(UUID categoryId);
}
