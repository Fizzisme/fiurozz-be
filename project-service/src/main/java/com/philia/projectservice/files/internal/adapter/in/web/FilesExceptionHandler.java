package com.philia.projectservice.files.internal.adapter.in.web;

import com.philia.projectservice.files.internal.domain.exception.FilesException;
import com.philia.projectservice.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class FilesExceptionHandler {
    @ExceptionHandler(FilesException.class)
    public ResponseEntity<ApiResponse<Object>> handle(FilesException exception) {
        var response = ResponseEntity.status(exception.status());
        if (exception.currentRevision() != null) response.eTag("\"" + exception.currentRevision() + "\"");
        return response.body(new ApiResponse<>(false, exception.code(), exception.getMessage(),
                exception.lease(), Map.of(), Instant.now()));
    }
}
