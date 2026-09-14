package com.philia.projectservice.catalog.internal.adapter.in.web.mapper;

import com.philia.projectservice.catalog.api.UpdateProjectCommand;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.UpdateProjectRequest;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface UpdateProjectWebMapper {

    default UpdateProjectCommand toCommand(UUID projectId, long expectedVersion, UpdateProjectRequest request) {
        return new UpdateProjectCommand(
                projectId, expectedVersion, request.subCategoryId(), request.title(), request.slug(),
                request.shortDescription(), request.description(), request.demoUrl(), request.techStack(), request.features()
        );
    }
}
