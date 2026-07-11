package com.northstar.api;

import com.northstar.core.calendar.CalendarEventNotFoundException;
import com.northstar.core.automation.AutomationDefinitionNotFoundException;
import com.northstar.core.discipline.DisciplineNotFoundException;
import com.northstar.core.finance.FinancePlanningNotFoundException;
import com.northstar.core.finance.TransactionNotFoundException;
import com.northstar.core.note.NoteNotFoundException;
import com.northstar.core.project.ProjectNotFoundException;
import com.northstar.core.study.StudySessionNotFoundException;
import com.northstar.core.study.VocabCardNotFoundException;
import com.northstar.core.task.TaskNotFoundException;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global error boundary: every error leaves as RFC 9457 ProblemDetail.
 * Extending {@link ResponseEntityExceptionHandler} gives all Spring MVC
 * framework exceptions (unreadable body, method not supported, handler-method
 * validation, ...) the same shape; on top of that we map domain exceptions and
 * enrich Bean Validation failures with a per-field {@code errors} property.
 * Ordered before Boot's problemdetails advice so these handlers win.
 */
@NullMarked
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({AutomationDefinitionNotFoundException.class, NoteNotFoundException.class, TaskNotFoundException.class,
            CalendarEventNotFoundException.class, DisciplineNotFoundException.class,
            ProjectNotFoundException.class, TransactionNotFoundException.class,
            FinancePlanningNotFoundException.class, StudySessionNotFoundException.class,
            VocabCardNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Domain guard rejected the request (bad reference id, invalid span, ...). */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(WebResearchException.class)
    ProblemDetail webResearch(WebResearchException e) {
        HttpStatus status = switch (e.code()) {
            case DISABLED, NOT_CONFIGURED -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
            case INVALID_REQUEST, UNSUPPORTED, BLOCKED, RESPONSE_TOO_LARGE -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        detail.setProperty("code", e.code().name());
        return detail;
    }

    /** Concurrent write or uniqueness conflict: the requested state is no longer applicable. */
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Keep the thrower's status (e.g. 400 from request guards) — never let the 500 fallback swallow it. */
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

    /** Body validation (@Valid on a request record): expose field → message so the UI can inline errors. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> errors.putIfAbsent(f.getField(),
                        Objects.requireNonNullElse(f.getDefaultMessage(), "invalid value")));
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
