package com.printshop.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 携带 HTTP 状态码的业务异常。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
