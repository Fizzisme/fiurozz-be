package com.philia.projectservice.catalog.internal.domain;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectSlugTest {

    @Test
    void generatesSlugFromVietnameseTitle() {
        assertThat(ProjectSlug.fromTitle(" Đồ án Tốt nghiệp 2026!! ").value())
                .isEqualTo("do-an-tot-nghiep-2026");
    }

    @Test
    void rejectsTitleThatCannotProduceASlug() {
        assertThatThrownBy(() -> ProjectSlug.fromTitle("!!!"))
                .isInstanceOf(InvalidProjectException.class)
                .hasMessage("Project slug must not be blank");
    }
}
