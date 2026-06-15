package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Response DTO for a single note on a trouble ticket.
 *
 * WHY Java record (not class):
 *   DTOs are pure data carriers — immutable, no behaviour. Records eliminate boilerplate
 *   (constructor, getters, equals, hashCode, toString) and make immutability explicit.
 *   Available since Java 16, idiomatic in Java 21 projects.
 *
 * WHY field name "date" (not "createdAt"):
 *   The OpenAPI contract uses "date" as the note timestamp field name. Using the contract
 *   name directly in the record means Jackson serialises it correctly without any
 *   @JsonProperty renaming. The mapper passes note.getCreatedAt() into this field.
 *
 * WHY @JsonInclude(NON_NULL):
 *   Prevents serialising null fields (e.g. if date is somehow missing) into "date": null,
 *   which would break clients that expect the field to always be a string.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoteResponse(
        String id,
        String text,
        LocalDateTime date
) {
}
