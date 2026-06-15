package com.netia.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uniform error response body returned for all 4xx / 5xx responses.
 *
 * WHY a shared error schema (not per-exception bodies):
 *   Clients need to parse errors programmatically. A single, predictable structure means
 *   clients write one error-handling path instead of branching on response shape.
 *   This matches the TMF API error convention used in telco integrations.
 *
 * Fields:
 *   code      — machine-readable error identifier (e.g. "TROUBLE_TICKET_NOT_FOUND").
 *               Clients switch on this, not on the HTTP status code alone.
 *   message   — human-readable description in Polish (matches the contract language).
 *   requestId — X-Request-ID echoed back so clients can reference a specific request
 *               when reporting issues to support (correlates with server logs).
 *
 * WHY @JsonInclude(NON_NULL):
 *   requestId is null when the error occurs before the RequestIdFilter runs (rare, e.g.
 *   container-level errors). NON_NULL prevents an explicit "requestId": null in the JSON
 *   which would confuse clients checking for its presence.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private String requestId;
}
