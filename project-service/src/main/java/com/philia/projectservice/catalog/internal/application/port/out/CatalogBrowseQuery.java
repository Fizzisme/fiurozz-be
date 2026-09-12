package com.philia.projectservice.catalog.internal.application.port.out;

import java.util.List;
import java.util.UUID;

public interface CatalogBrowseQuery {
    List<Category> listCategories();
    List<SubCategory> listSubCategories(UUID categoryId);
    TagPage searchTags(String query, int page, int size);

    record Category(UUID id, String key, String slug, String title, String icon, int sortOrder) { }
    record SubCategory(UUID id, UUID categoryId, String key, String slug, String title, int sortOrder) { }
    record Tag(UUID id, String slug, String displayName) { }
    record TagPage(List<Tag> items, int page, int size, long totalElements, int totalPages) { }
}
