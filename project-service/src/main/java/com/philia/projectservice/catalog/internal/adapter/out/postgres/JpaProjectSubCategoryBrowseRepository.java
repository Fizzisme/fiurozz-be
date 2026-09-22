package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface JpaProjectSubCategoryBrowseRepository extends Repository<ProjectSubCategoryJpaEntity, UUID> {
    List<ProjectSubCategoryJpaEntity> findByCategory_IdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(UUID categoryId);

    @Query("""
            SELECT subCategory
            FROM ProjectSubCategoryJpaEntity subCategory
            JOIN FETCH subCategory.category category
            WHERE category.id IN :categoryIds
              AND category.active = true
              AND category.deletedAt IS NULL
              AND subCategory.active = true
              AND subCategory.deletedAt IS NULL
            ORDER BY category.sortOrder ASC, category.title ASC,
                     subCategory.sortOrder ASC, subCategory.title ASC
            """)
    List<ProjectSubCategoryJpaEntity> findActiveByCategoryIds(@Param("categoryIds") Collection<UUID> categoryIds);
}
