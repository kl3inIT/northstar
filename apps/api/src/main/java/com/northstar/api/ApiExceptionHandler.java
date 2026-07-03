package com.northstar.api;

import com.northstar.core.note.NoteNotFoundException;
import com.northstar.core.task.TaskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Global error boundary: domain exceptions map to RFC 9457 ProblemDetail here,
 * keeping controllers free of error plumbing. Framework-raised errors (unknown
 * path, method not allowed, unreadable body) are covered by
 * {@code spring.mvc.problemdetails.enabled=true} in application.yml.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({NoteNotFoundException.class, TaskNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Keep the thrower's status (e.g. 400 from validation guards) — never let the 500 fallback swallow it. */
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail statusException(ResponseStatusException e) {
        ProblemDetail body = e.getBody();
        if (body.getDetail() == null && e.getReason() != null) {
            body.setDetail(e.getReason());
        }
        return body;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }
}
