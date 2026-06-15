package com.netia.common.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final String errorCode;
    private final String requestId;

    public ApiException(String errorCode, String message) {
        // WHY explicit cast to String: without it the compiler sees both
        // ApiException(String,String,String) and ApiException(String,String,Throwable)
        // as equally valid matches for null — ambiguous call — and refuses to compile.
        this(errorCode, message, (String) null);
    }

    public ApiException(String errorCode, String message, String requestId) {
        super(message);
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    public ApiException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.requestId = null;
    }
}

