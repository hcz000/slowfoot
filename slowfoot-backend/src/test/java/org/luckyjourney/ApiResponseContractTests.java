package org.luckyjourney;

import org.junit.jupiter.api.Test;
import org.hcz.exception.ApiErrorDetail;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.exception.ExceptionController;
import org.hcz.util.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseContractTests {

    private final ExceptionController exceptionController = new ExceptionController();

    @Test
    void successResponseExposesSuccessFields() {
        ApiResponse<String> response = ApiResponse.<String>ok().data("ready");

        assertTrue(response.getSuccess());
        assertEquals("OK", response.getErrorCode());
        assertEquals("ready", response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void appExceptionUsesErrorCodeHttpStatusAndDetails() {
        AppException exception = new AppException(
                ErrorCode.DUPLICATE_RESOURCE,
                "閭宸茶娉ㄥ唽",
                List.of(new ApiErrorDetail("email", "閭宸茶娉ㄥ唽")));

        ResponseEntity<ApiResponse<Object>> response = exceptionController.handleAppException(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().getSuccess());
        assertEquals("DUPLICATE_RESOURCE", response.getBody().getErrorCode());
        assertEquals(1, response.getBody().getDetails().size());
    }

    @Test
    void unexpectedExceptionsDoNotExposeInternalMessage() {
        ResponseEntity<ApiResponse<Object>> response = exceptionController
                .handleUnexpectedException(new IllegalStateException("database password"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getDefaultMessage(), response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("database password"));
    }
}
