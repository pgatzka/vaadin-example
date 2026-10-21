package dev.gatzka.flowboard.api;

import dev.gatzka.flowboard.exchange.InvalidBoardFileException;
import dev.gatzka.flowboard.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the domain's exceptions into small JSON error bodies with fitting status codes. */
@RestControllerAdvice(assignableTypes = BoardsApi.class)
class ApiErrorHandler {

    record ApiError(String error) {
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException missing) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(missing.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, InvalidBoardFileException.class})
    ResponseEntity<ApiError> badRequest(RuntimeException invalid) {
        return ResponseEntity.badRequest().body(new ApiError(invalid.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException invalid) {
        Throwable cause = invalid.getMostSpecificCause();
        String reason = cause.getMessage() == null ? "" : cause.getMessage().lines().findFirst().orElse("");
        String message = cause instanceof IllegalArgumentException ? reason : "The request body could not be read: " + reason;
        return ResponseEntity.badRequest().body(new ApiError(message));
    }
}
