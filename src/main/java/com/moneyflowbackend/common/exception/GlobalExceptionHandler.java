package com.moneyflowbackend.common.exception;

import com.moneyflowbackend.common.dto.ErrorResponse;
import com.moneyflowbackend.security.RateLimitExceededException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code(ex.getCode())
                .message(safeBusinessMessage(ex))
                .timestamp(clock.instant().toString())
                .fieldErrors(ex.getFieldErrors())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(res);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
        });
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code("VALIDATION_ERROR")
                .message("Dữ liệu nhập vào không hợp lệ.")
                .timestamp(clock.instant().toString())
                .fieldErrors(errors)
                .build();
        return ResponseEntity.badRequest().body(res);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code("RATE_LIMITED")
                .message("Bạn thao tác quá nhanh. Vui lòng thử lại sau.")
                .timestamp(clock.instant().toString())
                .retryAfterSeconds(ex.getRetryAfterSeconds())
                .fieldErrors(Collections.emptyMap())
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds()))
                .body(res);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(DataAccessResourceFailureException ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code("INTERNAL_ERROR")
                .message("Không thể hoàn tất yêu cầu. Vui lòng thử lại.")
                .timestamp(clock.instant().toString())
                .fieldErrors(Collections.emptyMap())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(res);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseError(DataAccessException ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code("INTERNAL_ERROR")
                .message("Không thể hoàn tất yêu cầu. Vui lòng thử lại.")
                .timestamp(clock.instant().toString())
                .fieldErrors(Collections.emptyMap())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code("INTERNAL_ERROR")
                .message("Không thể hoàn tất yêu cầu. Vui lòng thử lại.")
                .timestamp(clock.instant().toString())
                .fieldErrors(Collections.emptyMap())
                .build();
        return ResponseEntity.internalServerError().body(res);
    }

    private String safeBusinessMessage(BusinessException ex) {
        return switch (ex.getCode()) {
            case "UNAUTHORIZED" -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
            case "FORBIDDEN" -> "Bạn không có quyền thực hiện thao tác này.";
            case "STORAGE_NOT_CONFIGURED" -> "Kho lưu trữ âm thanh chưa được cấu hình.";
            default -> ex.getMessage();
        };
    }
}
