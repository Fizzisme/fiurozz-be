package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.CatalogBrowseQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JpaCatalogBrowseQuery implements CatalogBrowseQuery {
    private final JpaProjectCategoryBrowseRepository categories;
    private final JpaProjectSubCategoryBrowseRepository subCategories;
    private final JpaProjectTagBrowseRepository tags;

    public JpaCatalogBrowseQuery(JpaProjectCategoryBrowseRepository categories,
                                 JpaProjectSubCategoryBrowseRepository subCategories,
                                 JpaProjectTagBrowseRepository tags) {
        this.categories = categories;
        this.subCategories = subCategories;
        this.tags = tags;
    }
    @Override public List<Category> listCategories() {
        return categories.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(value -> new Category(value.getId(), value.getKey(), value.getSlug(), value.getTitle(), value.getIcon(), value.getSortOrder())).toList();
    }
    @Override public List<SubCategory> listSubCategories(UUID categoryId) {
        return subCategories.findByCategory_IdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(categoryId).stream()
                .map(value -> new SubCategory(value.getId(), categoryId, value.getKey(), value.getSlug(), value.getTitle(), value.getSortOrder())).toList();
    }
    @Override public List<CategoryTree> listCategoryTree() {
        var activeCategories = categories.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc();
        if (activeCategories.isEmpty()) {
            return List.of();
        }

        var categoryIds = activeCategories.stream().map(ProjectCategoryJpaEntity::getId).toList();
        Map<UUID, List<TreeSubCategory>> childrenByCategory = subCategories.findActiveByCategoryIds(categoryIds).stream()
                .collect(Collectors.groupingBy(
                        value -> value.getCategory().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(value -> new TreeSubCategory(
                                value.getId(), value.getKey(), value.getSlug(), value.getTitle(), value.getSortOrder()
                        ), Collectors.toList())
                ));

        return activeCategories.stream()
                .map(category -> new CategoryTree(
                        category.getId(), category.getKey(), category.getSlug(), category.getTitle(), category.getIcon(),
                        category.getSortOrder(), childrenByCategory.getOrDefault(category.getId(), List.of())
                ))
                .toList();
    }
    @Override public TagPage searchTags(String query, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = query == null || query.isBlank()
                ? tags.findActive(pageable)
                : tags.searchActive(query.trim(), pageable);
        return new TagPage(result.getContent().stream().map(tag -> new Tag(tag.getId(), tag.getSlug(), tag.getDisplayName())).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
