package com.quberratrix.identity.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class IdentityException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public IdentityException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
