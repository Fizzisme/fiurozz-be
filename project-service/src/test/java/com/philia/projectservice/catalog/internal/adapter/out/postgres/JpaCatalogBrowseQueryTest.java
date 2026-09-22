package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaCatalogBrowseQueryTest {

    @Test
    void loadsCategoryTreeWithOneCategoryQueryAndOneBatchSubCategoryQuery() {
        var categoryId = UUID.randomUUID();
        var emptyCategoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();

        var category = category(categoryId, "developer-tools", "Developer Tools", 10);
        var emptyCategory = category(emptyCategoryId, "education", "Education", 20);
        var subCategory = mock(ProjectSubCategoryJpaEntity.class);
        when(subCategory.getId()).thenReturn(subCategoryId);
        when(subCategory.getCategory()).thenReturn(category);
        when(subCategory.getKey()).thenReturn("api-platform");
        when(subCategory.getSlug()).thenReturn("api-platform");
        when(subCategory.getTitle()).thenReturn("API Platform");
        when(subCategory.getSortOrder()).thenReturn(10);

        var categoryRepository = mock(JpaProjectCategoryBrowseRepository.class);
        var subCategoryRepository = mock(JpaProjectSubCategoryBrowseRepository.class);
        var tagRepository = mock(JpaProjectTagBrowseRepository.class);
        when(categoryRepository.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc())
                .thenReturn(List.of(category, emptyCategory));
        when(subCategoryRepository.findActiveByCategoryIds(List.of(categoryId, emptyCategoryId)))
                .thenReturn(List.of(subCategory));

        var result = new JpaCatalogBrowseQuery(categoryRepository, subCategoryRepository, tagRepository)
                .listCategoryTree();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().subCategories()).singleElement()
                .satisfies(value -> assertThat(value.id()).isEqualTo(subCategoryId));
        assertThat(result.get(1).subCategories()).isEmpty();
        verify(categoryRepository, times(1)).findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc();
        verify(subCategoryRepository, times(1)).findActiveByCategoryIds(List.of(categoryId, emptyCategoryId));
    }

    private static ProjectCategoryJpaEntity category(UUID id, String slug, String title, int sortOrder) {
        var category = mock(ProjectCategoryJpaEntity.class);
        when(category.getId()).thenReturn(id);
        when(category.getKey()).thenReturn(slug);
        when(category.getSlug()).thenReturn(slug);
        when(category.getTitle()).thenReturn(title);
        when(category.getIcon()).thenReturn("code");
        when(category.getSortOrder()).thenReturn(sortOrder);
        return category;
    }
}
