package com.moneyflowbackend.common.exception;

import com.moneyflowbackend.common.dto.ErrorResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code(ex.getCode())
                .message(ex.getMessage())
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
                .fieldErrors(errors)
                .build();
        return ResponseEntity.badRequest().body(res);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(DataAccessResourceFailureException ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code("DATABASE_UNAVAILABLE")
                .message("Không thể kết nối cơ sở dữ liệu.")
                .fieldErrors(Collections.emptyMap())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        ErrorResponse res = ErrorResponse.builder()
                .success(false)
                .code("INTERNAL_SERVER_ERROR")
                .message("Đã xảy ra lỗi hệ thống.")
                .fieldErrors(Collections.emptyMap())
                .build();
        return ResponseEntity.internalServerError().body(res);
    }
}
