package com.netia.common.util;

import java.util.UUID;

public class RequestIdHolder {
    private static final ThreadLocal<String> requestId = new ThreadLocal<>();

    public static void setRequestId(String id) {
        requestId.set(id);
    }

    public static String getRequestId() {
        String id = requestId.get();
        return id != null ? id : "no-request-id";
    }

    public static void clear() {
        requestId.remove();
    }

    public static String generateRequestId() {
        String id = "req-" + UUID.randomUUID().toString().substring(0, 8);
        setRequestId(id);
        return id;
    }
}

