package com.netia.common.exception;

/**
 * Thrown when a requested resource does not exist or is outside the caller's tenant scope.
 *
 * WHY 404 for both "not found" and "wrong tenant" (IDOR protection):
 *   Returning 403 would confirm the resource EXISTS — an attacker could enumerate valid IDs
 *   by watching for 403 vs 404. Returning 404 for both cases prevents information disclosure.
 */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}

