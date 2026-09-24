package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.internal.application.port.out.CatalogBrowseQuery;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogBrowseControllerTest {

    @Test
    void returnsCategoryTreeInOneResponse() {
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var query = mock(CatalogBrowseQuery.class);
        when(query.listCategoryTree()).thenReturn(List.of(new CatalogBrowseQuery.CategoryTree(
                categoryId,
                "developer-tools",
                "developer-tools",
                "Developer Tools",
                "code",
                10,
                List.of(new CatalogBrowseQuery.TreeSubCategory(
                        subCategoryId, "api-platform", "api-platform", "API Platform", 20
                ))
        )));

        var response = new CatalogBrowseController(query).listCategoryTree();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PROJECT_CATEGORY_TREE_RETRIEVED");
        assertThat(response.getBody().data().items()).singleElement().satisfies(category -> {
            assertThat(category.id()).isEqualTo(categoryId);
            assertThat(category.subCategories()).singleElement()
                    .satisfies(subCategory -> assertThat(subCategory.id()).isEqualTo(subCategoryId));
        });
    }
}
