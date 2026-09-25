package com.philia.projectservice.catalog.internal.adapter.in.web.mapper;

import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateProjectWebMapperTest {

    private final CreateProjectWebMapper mapper = Mappers.getMapper(CreateProjectWebMapper.class);

    @Test
    void mapsRequestToApplicationCommand() {
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var request = new CreateProjectRequest(
                subCategoryId,
                "Fiurozz Backend",
                "Short description",
                "Full description",
                "https://demo.example.com",
                "https://github.com/fizzisme/fiurozz-be",
                "PRIVATE",
                List.of("java"),
                List.of("Project catalog"),
                List.of(tagId)
        );

        var command = mapper.toCommand(request);

        assertThat(command.subCategoryId()).isEqualTo(subCategoryId);
        assertThat(command.title()).isEqualTo("Fiurozz Backend");
        assertThat(command.githubUrl()).isEqualTo("https://github.com/fizzisme/fiurozz-be");
        assertThat(command.tagIds()).containsExactly(tagId);
    }
}
