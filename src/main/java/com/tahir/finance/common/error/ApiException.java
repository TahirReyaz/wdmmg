package com.tahir.finance.common.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Every deliberate failure in the application throws one of these, so the
 * exception handler can build an RFC 9457 ProblemDetail without guesswork.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final transient Map<String, Object> properties;

    public ApiException(HttpStatus status, String errorCode, String detail) {
        this(status, errorCode, detail, Map.of());
    }

    public ApiException(HttpStatus status, String errorCode, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
        this.properties = properties;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    // ---- factories for the cases the API actually returns -------------------

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " was not found.");
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException unprocessable(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    public static ApiException forbidden(String code, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail);
    }

    public static ApiException forbidden(String code, String detail, Map<String, Object> properties) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail, properties);
    }

    public static ApiException unauthorized(String code, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }
}
