package com.netia.troubleticket.service;

import com.netia.common.exception.ResourceNotFoundException;
import com.netia.common.exception.ValidationException;
import com.netia.common.idempotency.IdempotencyService;
import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.dto.NoteCreateRequest;
import com.netia.troubleticket.dto.TroubleTicketCloseStatusRequest;
import com.netia.troubleticket.dto.TroubleTicketCreateRequest;
import com.netia.troubleticket.dto.TroubleTicketResponse;
import com.netia.troubleticket.mapper.TroubleTicketMapper;
import com.netia.troubleticket.repository.TroubleTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TroubleTicketService business logic.
 *
 * WHY @ExtendWith(MockitoExtension.class) (not @SpringBootTest):
 *   Unit tests must be fast and isolated. @SpringBootTest starts the full context
 *   (DB, Redis, security) which is ~10x slower. Mockito gives full control without
 *   any infrastructure.
 *
 * WHY we assert on errorCode via instanceof + cast (not hasMessageContaining):
 *   ResourceNotFoundException carries the error code in the 'errorCode' field,
 *   NOT in the exception message. getMessage() returns the Polish description.
 *   assertThatThrownBy(...).isInstanceOf(ResourceNotFoundException.class) is
 *   sufficient to verify the correct exception type; we additionally check errorCode
 *   via satisfies() to keep the test readable without casting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TroubleTicketService — unit tests")
class TroubleTicketServiceTest {

    @Mock private TroubleTicketRepository repository;
    @Mock private TroubleTicketMapper     mapper;
    @Mock private IdempotencyService      idempotencyService;

    @InjectMocks
    private TroubleTicketService service;

    // ── createTroubleTicket ───────────────────────────────────────────────────

    @Test
    @DisplayName("create: rejects status other than 'new'")
    void create_invalidStatus_throwsValidationException() {
        TroubleTicketCreateRequest request = new TroubleTicketCreateRequest(
                "EXT-001", 1L, "desc", "closed", "note");

        assertThatThrownBy(() -> service.createTroubleTicket(request, "t1", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("niedozwoloną wartość");
    }

    @Test
    @DisplayName("create: returns existing ticket when externalId already exists (idempotency Layer 2)")
    void create_duplicateExternalId_returnsExisting() {
        TroubleTicketCreateRequest request = new TroubleTicketCreateRequest(
                "EXT-DUP", 99L, "Duplicate", "new", "note");

        TroubleTicket existing = TroubleTicket.builder()
                .id("TT-EXISTING")
                .tenantId("t1")
                .externalId("EXT-DUP")
                .serviceId(99L)
                .description("Duplicate")
                .status(TroubleTicketStatus.ACKNOWLEDGED)
                .build();

        TroubleTicketResponse expectedResponse = new TroubleTicketResponse(
                "TT-EXISTING", "EXT-DUP", 99L, "Duplicate", "acknowledged", List.of());

        when(repository.findByExternalIdAndTenantId("EXT-DUP", "t1")).thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(expectedResponse);

        TroubleTicketService.CreateResult result = service.createTroubleTicket(request, "t1", null);

        assertThat(result.isNew()).isFalse();
        assertThat(result.ticket().id()).isEqualTo("TT-EXISTING");
    }

    // ── closeTroubleTicket ────────────────────────────────────────────────────

    @Test
    @DisplayName("close: rejects status other than 'closed'")
    void close_invalidStatus_throwsValidationException() {
        assertThatThrownBy(() -> service.closeTroubleTicket(
                "TT-1", new TroubleTicketCloseStatusRequest("inProgress"), "t1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("niedozwoloną wartość");
    }

    @Test
    @DisplayName("close: throws ResourceNotFoundException when ticket not found")
    void close_ticketNotFound_throwsResourceNotFoundException() {
        when(repository.findByIdAndTenantId("TT-MISSING", "t1")).thenReturn(Optional.empty());

        // WHY satisfies() instead of hasMessageContaining(errorCode):
        //   errorCode lives in ex.getErrorCode(), not in ex.getMessage().
        //   satisfies() lets us cast and assert the field without a separate variable.
        assertThatThrownBy(() -> service.closeTroubleTicket(
                "TT-MISSING", new TroubleTicketCloseStatusRequest("closed"), "t1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                        .isEqualTo(TroubleTicketService.TICKET_NOT_FOUND_CODE));
    }

    // ── getTroubleTicketById ──────────────────────────────────────────────────

    @Test
    @DisplayName("get: throws ResourceNotFoundException when ticket does not exist")
    void get_nonExistent_throwsResourceNotFoundException() {
        when(repository.findByIdAndTenantId("TT-X", "t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTroubleTicketById("TT-X", "t1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                        .isEqualTo(TroubleTicketService.TICKET_NOT_FOUND_CODE));
    }

    @Test
    @DisplayName("get: tenant isolation — ticket of tenant-A is invisible to tenant-B")
    void get_wrongTenant_throwsResourceNotFoundException() {
        when(repository.findByIdAndTenantId("TT-123", "t-B")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTroubleTicketById("TT-123", "t-B"))
                .isInstanceOf(ResourceNotFoundException.class);

        // Verify the repository was called with the correct tenant scope.
        verify(repository).findByIdAndTenantId("TT-123", "t-B");
    }

    // ── addNote ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addNote: throws ResourceNotFoundException when ticket not found")
    void addNote_ticketNotFound_throwsResourceNotFoundException() {
        when(repository.findByIdAndTenantId("TT-MISSING", "t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addNote("TT-MISSING", new NoteCreateRequest("text"), "t1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                        .isEqualTo(TroubleTicketService.TICKET_NOT_FOUND_CODE));
    }
}
