package com.philia.projectservice.shared.web;

import com.philia.projectservice.catalog.internal.application.exception.CurrentActorUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.MediaStorageUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.OwnerProfileUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectMediaValidationException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectForbiddenException;
import com.philia.projectservice.catalog.internal.application.exception.SubCategoryUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectStaleVersionException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotEditableException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotDeletableException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectInvalidStateException;
import com.philia.projectservice.catalog.internal.application.exception.TagsUnavailableException;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public final class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(ApiResponse.validationFailure(errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is not valid JSON.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.validationFailure(
                Map.of(exception.getRequestPartName(), exception.getRequestPartName() + " part is required")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        // Also raised for a multipart part, for example a "project" part sent as text/plain.
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "The request exceeds the maximum upload size.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER", "A request parameter has an invalid value.");
    }

    @ExceptionHandler(InvalidProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidProject(InvalidProjectException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
    }

    @ExceptionHandler(CurrentActorUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleCurrentActor(CurrentActorUnavailableException exception) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(OwnerProfileUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleOwnerProfileUnavailable(OwnerProfileUnavailableException exception) {
        return error(HttpStatus.CONFLICT, "OWNER_PROFILE_NOT_READY", exception.getMessage());
    }

    @ExceptionHandler(ProjectSlugAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleSlugConflict(ProjectSlugAlreadyExistsException exception) {
        return error(HttpStatus.CONFLICT, "PROJECT_SLUG_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotFound(ProjectNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ProjectForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectForbidden(ProjectForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, "PROJECT_FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(ProjectStaleVersionException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectStaleVersion(ProjectStaleVersionException exception) {
        return error(HttpStatus.PRECONDITION_FAILED, "PROJECT_STALE_VERSION", exception.getMessage());
    }

    @ExceptionHandler(ProjectNotEditableException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotEditable(ProjectNotEditableException exception) {
        return error(HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE", exception.getMessage());
    }

    @ExceptionHandler(ProjectNotDeletableException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotDeletable(ProjectNotDeletableException exception) {
        return error(HttpStatus.CONFLICT, "PROJECT_NOT_DELETABLE", exception.getMessage());
    }

    @ExceptionHandler(ProjectInvalidStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectInvalidState(ProjectInvalidStateException exception) {
        return error(HttpStatus.CONFLICT, "PROJECT_INVALID_STATE", exception.getMessage());
    }

    @ExceptionHandler(SubCategoryUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleSubCategory(SubCategoryUnavailableException exception) {
        return error(HttpStatus.CONFLICT, "SUBCATEGORY_NOT_AVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(TagsUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleTags(TagsUnavailableException exception) {
        return error(HttpStatus.CONFLICT, "TAGS_NOT_AVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(ProjectMediaValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaValidation(ProjectMediaValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationFailure(Map.of(exception.field(), exception.getMessage())));
    }

    @ExceptionHandler(MediaStorageUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaStorage(MediaStorageUnavailableException exception) {
        // The cause carries the storage error; the client only gets a generic message.
        log.error("Project media storage failure", exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE",
                "Media storage is temporarily unavailable. Please try again.");
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
    }
}
