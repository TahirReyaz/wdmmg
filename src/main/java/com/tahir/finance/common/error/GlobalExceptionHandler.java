package com.tahir.finance.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Turns everything into RFC 9457 Problem Details with a stable machine-readable
 * "code", so the frontend can branch on the code rather than on English prose.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://api.wheredidmymoneygo.app/errors/";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = base(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
        ex.getProperties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();

        ProblemDetail problem = base(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                "One or more fields are invalid.", request);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return base(HttpStatus.BAD_REQUEST, "MALFORMED_BODY",
                "The request body could not be parsed as JSON.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return base(HttpStatus.BAD_REQUEST, "BAD_PARAMETER",
                "Parameter '" + ex.getName() + "' has an unexpected value.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return base(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. The failure has been logged.", request);
    }

    private Map<String, String> fieldError(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
    }

    private ProblemDetail base(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(BASE_TYPE + code.toLowerCase().replace('_', '-')));
        problem.setTitle(titleFor(status));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }

    private String titleFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Not found";
            case CONFLICT -> "Conflict";
            case UNPROCESSABLE_ENTITY -> "Validation failed";
            case FORBIDDEN -> "Forbidden";
            case UNAUTHORIZED -> "Unauthorized";
            case TOO_MANY_REQUESTS -> "Too many requests";
            case BAD_REQUEST -> "Bad request";
            default -> "Unexpected error";
        };
    }
}
