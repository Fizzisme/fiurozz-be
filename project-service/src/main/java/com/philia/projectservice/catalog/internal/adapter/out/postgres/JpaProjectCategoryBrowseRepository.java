package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.repository.Repository;
import java.util.List;

interface JpaProjectCategoryBrowseRepository extends Repository<ProjectCategoryJpaEntity, java.util.UUID> {
    List<ProjectCategoryJpaEntity> findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc();
}
