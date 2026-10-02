package com.philia.projectservice.catalog.internal.adapter.in.web.mapper;

import com.philia.projectservice.catalog.api.CreateProjectCommand;
import com.philia.projectservice.catalog.api.ProjectMediaUpload;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CreateProjectWebMapper {

    @Mapping(target = "images", source = "images")
    @Mapping(target = "video", source = "video")
    CreateProjectCommand toCommand(CreateProjectRequest request, List<ProjectMediaUpload> images,
                                   ProjectMediaUpload video);
}
