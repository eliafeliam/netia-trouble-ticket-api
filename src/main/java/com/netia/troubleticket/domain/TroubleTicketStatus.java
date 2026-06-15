package com.netia.troubleticket.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Ticket lifecycle statuses defined by the OpenAPI contract.
 *
 * WHY @JsonValue + @JsonCreator instead of "new_" workaround:
 *   "new" is a reserved keyword in Java, so we cannot name an enum constant "new".
 *   The naive workaround (naming it "new_" and overriding toString) is fragile:
 *   Hibernate's EnumType.STRING serialises the enum name ("new_"), not toString(),
 *   so the stored DB value would be "new_" rather than "new", requiring extra mapper
 *   logic everywhere. The clean solution uses @JsonValue / @JsonCreator for the API
 *   layer and TroubleTicketStatusConverter for the DB layer.
 *
 * WHY Arrays.stream in fromValue (not a manual for-loop):
 *   Stream + filter + findFirst is the idiomatic Java functional style for enum lookup.
 *   It is declarative ("find the one that matches") rather than imperative ("iterate
 *   and break"), and composes naturally with Optional.
 */
public enum TroubleTicketStatus {

    NEW("new"),
    ACKNOWLEDGED("acknowledged"),
    IN_PROGRESS("inProgress"),
    RESOLVED("resolved"),
    CLOSED("closed"),
    REJECTED("rejected");

    private final String value;

    TroubleTicketStatus(String value) {
        this.value = value;
    }

    /** Canonical string for JSON responses and DB storage — matches the OpenAPI contract. */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Deserialises from the API / DB string back to an enum constant.
     * Case-insensitive so "New", "NEW", "new" all map to NEW.
     *
     * WHY Optional + orElseThrow (not a for-loop with break):
     *   Functional style is more expressive and eliminates the mutable loop variable.
     *   The Optional chain reads as: "find the status whose value matches, or throw."
     */
    @JsonCreator
    public static TroubleTicketStatus fromValue(String value) {
        if (value == null) return null;
        return Arrays.stream(values())
                .filter(s -> s.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TroubleTicketStatus: " + value));
    }
}
