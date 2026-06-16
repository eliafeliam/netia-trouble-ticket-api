package com.netia.troubleticket.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DTO Bean Validation — unit tests")
class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private <T> Set<ConstraintViolation<T>> validate(T obj) {
        return validator.validate(obj);
    }

    private boolean hasViolationOnField(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    // ── TroubleTicketCreateRequest ────────────────────────────────────────────

    @Nested
    @DisplayName("TroubleTicketCreateRequest")
    class CreateRequest {

        private TroubleTicketCreateRequest valid() {
            return new TroubleTicketCreateRequest("EXT-1", 1L, "desc", "new", "note");
        }

        @Test
        @DisplayName("valid request: no violations")
        void valid_noViolations() {
            assertThat(validate(valid())).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        @DisplayName("externalId blank/null: violation on externalId")
        void externalId_blank_violation(String externalId) {
            var req = new TroubleTicketCreateRequest(externalId, 1L, "desc", "new", "note");
            assertThat(hasViolationOnField(validate(req), "externalId")).isTrue();
        }

        @Test
        @DisplayName("externalId over 100 chars: violation")
        void externalId_tooLong_violation() {
            var req = new TroubleTicketCreateRequest("X".repeat(101), 1L, "desc", "new", "note");
            assertThat(hasViolationOnField(validate(req), "externalId")).isTrue();
        }

        @Test
        @DisplayName("externalId exactly 100 chars: no violation")
        void externalId_exactly100_noViolation() {
            var req = new TroubleTicketCreateRequest("X".repeat(100), 1L, "desc", "new", "note");
            assertThat(hasViolationOnField(validate(req), "externalId")).isFalse();
        }

        @Test
        @DisplayName("serviceId null: violation")
        void serviceId_null_violation() {
            var req = new TroubleTicketCreateRequest("EXT-1", null, "desc", "new", "note");
            assertThat(hasViolationOnField(validate(req), "serviceId")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -999L})
        @DisplayName("serviceId zero or negative: violation")
        void serviceId_zeroOrNegative_violation(long serviceId) {
            var req = new TroubleTicketCreateRequest("EXT-1", serviceId, "desc", "new", "note");
            assertThat(hasViolationOnField(validate(req), "serviceId")).isTrue();
        }

        @Test
        @DisplayName("serviceId = 1: no violation (boundary)")
        void serviceId_one_noViolation() {
            var req = new TroubleTicketCreateRequest("EXT-1", 1L, "desc", "new", "note");
            assertThat(hasViolationOnField(validate(req), "serviceId")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("description blank/null: violation")
        void description_blank_violation(String description) {
            var req = new TroubleTicketCreateRequest("EXT-1", 1L, description, "new", "note");
            assertThat(hasViolationOnField(validate(req), "description")).isTrue();
        }

        @Test
        @DisplayName("description over 2000 chars: violation")
        void description_tooLong_violation() {
            var req = new TroubleTicketCreateRequest("EXT-1", 1L, "X".repeat(2001), "new", "note");
            assertThat(hasViolationOnField(validate(req), "description")).isTrue();
        }

        @Test
        @DisplayName("description exactly 2000 chars: no violation")
        void description_exactly2000_noViolation() {
            var req = new TroubleTicketCreateRequest("EXT-1", 1L, "X".repeat(2000), "new", "note");
            assertThat(hasViolationOnField(validate(req), "description")).isFalse();
        }

        @Test
        @DisplayName("status null: violation")
        void status_null_violation() {
            var req = new TroubleTicketCreateRequest("EXT-1", 1L, "desc", null, "note");
            assertThat(hasViolationOnField(validate(req), "status")).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("note blank/null: violation")
        void note_blank_violation(String note) {
            var req = new TroubleTicketCreateRequest("EXT-1", 1L, "desc", "new", note);
            assertThat(hasViolationOnField(validate(req), "note")).isTrue();
        }

        @Test
        @DisplayName("note over 2000 chars: violation")
        void note_tooLong_violation() {
            var req = new TroubleTicketCreateRequest("EXT-1", 1L, "desc", "new", "X".repeat(2001));
            assertThat(hasViolationOnField(validate(req), "note")).isTrue();
        }

        @Test
        @DisplayName("multiple fields invalid: multiple violations returned")
        void multipleInvalidFields_multipleViolations() {
            var req = new TroubleTicketCreateRequest(null, null, "", "new", "");
            Set<ConstraintViolation<TroubleTicketCreateRequest>> violations = validate(req);
            assertThat(violations.size()).isGreaterThanOrEqualTo(3);
        }
    }

    // ── NoteCreateRequest ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("NoteCreateRequest")
    class NoteRequest {

        @Test
        @DisplayName("valid: no violations")
        void valid_noViolations() {
            assertThat(validate(new NoteCreateRequest("some note"))).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("text blank/null: violation")
        void text_blank_violation(String text) {
            assertThat(hasViolationOnField(validate(new NoteCreateRequest(text)), "text")).isTrue();
        }

        @Test
        @DisplayName("text over 2000 chars: violation")
        void text_tooLong_violation() {
            assertThat(hasViolationOnField(validate(new NoteCreateRequest("X".repeat(2001))), "text")).isTrue();
        }

        @Test
        @DisplayName("text exactly 2000 chars: no violation")
        void text_exactly2000_noViolation() {
            assertThat(hasViolationOnField(validate(new NoteCreateRequest("X".repeat(2000))), "text")).isFalse();
        }
    }

    // ── TroubleTicketCloseStatusRequest ───────────────────────────────────────

    @Nested
    @DisplayName("TroubleTicketCloseStatusRequest")
    class CloseRequest {

        @Test
        @DisplayName("valid: no violations")
        void valid_noViolations() {
            assertThat(validate(new TroubleTicketCloseStatusRequest("closed"))).isEmpty();
        }

        @Test
        @DisplayName("status null: violation")
        void status_null_violation() {
            assertThat(hasViolationOnField(validate(new TroubleTicketCloseStatusRequest(null)), "status")).isTrue();
        }
    }
}
