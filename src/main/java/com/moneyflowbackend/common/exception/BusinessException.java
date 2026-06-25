package com.moneyflowbackend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Map<String, String> fieldErrors;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String code, String message, Map<String, String> fieldErrors) {
        this(code, message, HttpStatus.BAD_REQUEST, fieldErrors);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, Collections.emptyMap());
    }

    public BusinessException(String code, String message, HttpStatus status, Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.status = status;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : Map.copyOf(fieldErrors);
    }
}
