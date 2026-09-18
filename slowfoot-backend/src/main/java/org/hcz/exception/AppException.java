package org.hcz.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;


// Base exception 用于 expected application errors.

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiErrorDetail> details;

    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, Collections.emptyList());
    }

    public AppException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, Collections.emptyList());
    }

    public AppException(ErrorCode errorCode, String message, List<ApiErrorDetail> details) {
        this(errorCode, message, null, details);
    }

    public AppException(ErrorCode errorCode, String message, Throwable cause,
                        List<ApiErrorDetail> details) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? Collections.emptyList() : List.copyOf(details);
    }
}
