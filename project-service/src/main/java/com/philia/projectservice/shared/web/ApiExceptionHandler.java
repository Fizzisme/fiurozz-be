package com.philia.projectservice.shared.web;

import com.philia.projectservice.catalog.internal.application.exception.CurrentActorUnavailableException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;

@RestControllerAdvice
public final class ApiExceptionHandler {

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

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
    }
}
