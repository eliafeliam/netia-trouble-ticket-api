package com.netia.common.exception;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}

