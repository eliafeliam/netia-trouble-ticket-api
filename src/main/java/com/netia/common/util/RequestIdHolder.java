package com.netia.common.util;

/**
 * Thread-local holder for the X-Request-ID of the current request.
 *
 * WHY ThreadLocal (not MDC directly):
 *   MDC is the right place for logging correlation, and we do write to MDC in
 *   RequestIdFilter. This holder provides programmatic access to the requestId
 *   from non-logging code (e.g. GlobalExceptionHandler building error response bodies).
 *   Without it, the handler would have to parse MDC strings — coupling error handling
 *   to the logging subsystem.
 *
 * WHY remove() in finally (not just set(null)):
 *   ThreadLocal.set(null) keeps the entry in the thread's map, which can cause memory
 *   leaks in thread pools (the garbage collector cannot collect the value). remove()
 *   fully cleans up the entry — mandatory with Java 21 virtual thread reuse.
 */
public final class RequestIdHolder {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestIdHolder() {}

    public static void setRequestId(String id) {
        REQUEST_ID.set(id);
    }

    public static String getRequestId() {
        String id = REQUEST_ID.get();
        return id != null ? id : "no-request-id";
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
