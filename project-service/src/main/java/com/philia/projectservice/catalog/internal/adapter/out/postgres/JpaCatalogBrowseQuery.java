package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.CatalogBrowseQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
    @Override public TagPage searchTags(String query, int page, int size) {
        var result = tags.searchActive(query == null || query.isBlank() ? null : query.trim(), PageRequest.of(page, size));
        return new TagPage(result.getContent().stream().map(tag -> new Tag(tag.getId(), tag.getSlug(), tag.getDisplayName())).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
