package com.philia.projectservice.catalog.internal.adapter.in.web.mapper;

import com.philia.projectservice.catalog.api.ProjectCursorResult;
import com.philia.projectservice.catalog.api.PublicProjectCardResult;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.PublicProjectCardResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.PublicProjectCursorPageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PublicProjectWebMapper {

    PublicProjectCardResponse toResponse(PublicProjectCardResult result);

    default PublicProjectCursorPageResponse toPageResponse(
            ProjectCursorResult<PublicProjectCardResult> result
    ) {
        return new PublicProjectCursorPageResponse(
                result.items().stream().map(this::toResponse).toList(),
                result.nextCursor() == null ? null : result.nextCursor().encode(),
                result.hasMore()
        );
    }
}
