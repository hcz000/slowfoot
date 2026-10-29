package org.hcz.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.hcz.common.util.ApiResponse;
import org.hcz.common.util.RequestIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;


// 转换 expected 并 unexpected failures into one API response contract.

@RestControllerAdvice
public class ExceptionController {

    private static final Logger log = LoggerFactory.getLogger(ExceptionController.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException exception) {
        String message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : exception.getErrorCode().getDefaultMessage();
        ApiResponse<Object> response = ApiResponse.error(exception.getErrorCode())
                .message(message)
                .details(exception.getDetails());
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        BindingResult bindingResult = exception.getBindingResult();
        List<ApiErrorDetail> details = bindingResult.getFieldErrors().stream()
                .map(error -> new ApiErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return errorResponse(ErrorCode.VALIDATION_ERROR, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiErrorDetail> details = exception.getConstraintViolations().stream()
                .map(error -> new ApiErrorDetail(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return errorResponse(ErrorCode.VALIDATION_ERROR, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadableMessage() {
        return errorResponse(ErrorCode.VALIDATION_ERROR,
                List.of(new ApiErrorDetail("body", "请求体格式不正确")));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Object>> handleInvalidRequestParameter(Exception exception) {
        String parameter = exception instanceof MissingServletRequestParameterException missing
                ? missing.getParameterName() : "request";
        return errorResponse(ErrorCode.VALIDATION_ERROR,
                List.of(new ApiErrorDetail(parameter, "请求参数格式不正确")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation, requestId={}", RequestIdContext.current(), exception);
        return errorResponse(ErrorCode.CONFLICT, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception, requestId={}", RequestIdContext.current(), exception);
        return errorResponse(ErrorCode.INTERNAL_ERROR, List.of());
    }

    private ResponseEntity<ApiResponse<Object>> errorResponse(ErrorCode errorCode, List<ApiErrorDetail> details) {
        ApiResponse<Object> response = ApiResponse.error(errorCode).details(details);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }
}
