package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.internal.application.port.out.CatalogBrowseQuery;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import com.philia.projectservice.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CatalogBrowseController {
    private final CatalogBrowseQuery query;
    public CatalogBrowseController(CatalogBrowseQuery query) { this.query = query; }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<ItemsResponse<CatalogBrowseQuery.Category>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.success("PROJECT_CATEGORIES_RETRIEVED", "Project categories retrieved successfully.",
                new ItemsResponse<>(query.listCategories())));
    }

    @GetMapping("/categories/tree")
    public ResponseEntity<ApiResponse<ItemsResponse<CatalogBrowseQuery.CategoryTree>>> listCategoryTree() {
        return ResponseEntity.ok(ApiResponse.success(
                "PROJECT_CATEGORY_TREE_RETRIEVED",
                "Project category tree retrieved successfully.",
                new ItemsResponse<>(query.listCategoryTree())
        ));
    }

    @GetMapping("/categories/{categoryId}/subcategories")
    public ResponseEntity<ApiResponse<ItemsResponse<CatalogBrowseQuery.SubCategory>>> listSubCategories(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(ApiResponse.success("PROJECT_SUBCATEGORIES_RETRIEVED", "Project subcategories retrieved successfully.",
                new ItemsResponse<>(query.listSubCategories(categoryId))));
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<CatalogBrowseQuery.TagPage>> searchTags(
            @RequestParam(name = "q", required = false) String queryText,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 50) throw new InvalidProjectException("Page must be zero or greater and size must be between 1 and 50.");
        return ResponseEntity.ok(ApiResponse.success("PROJECT_TAGS_RETRIEVED", "Project tags retrieved successfully.",
                query.searchTags(queryText, page, size)));
    }

    public record ItemsResponse<T>(List<T> items) {
        public ItemsResponse { items = List.copyOf(items); }
    }
}
