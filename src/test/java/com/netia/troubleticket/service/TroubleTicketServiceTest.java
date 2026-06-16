package com.netia.troubleticket.service;

import com.netia.common.exception.ResourceNotFoundException;
import com.netia.common.exception.ValidationException;
import com.netia.common.idempotency.IdempotencyService;
import com.netia.troubleticket.domain.Note;
import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.dto.*;
import com.netia.troubleticket.mapper.TroubleTicketMapper;
import com.netia.troubleticket.repository.TroubleTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TroubleTicketService — unit tests")
class TroubleTicketServiceTest {

    @Mock private TroubleTicketRepository repository;
    @Mock private TroubleTicketMapper     mapper;
    @Mock private IdempotencyService      idempotencyService;

    @InjectMocks
    private TroubleTicketService service;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TroubleTicket ticket(String id, String externalId, String tenantId) {
        return TroubleTicket.builder()
                .id(id).tenantId(tenantId).externalId(externalId)
                .serviceId(1L).description("desc")
                .status(TroubleTicketStatus.ACKNOWLEDGED)
                .build();
    }

    private TroubleTicketResponse response(String id, String externalId) {
        return new TroubleTicketResponse(id, externalId, 1L, "desc", "acknowledged", List.of());
    }

    private TroubleTicketCreateRequest createReq(String externalId, String status) {
        return new TroubleTicketCreateRequest(externalId, 1L, "desc", status, "note");
    }

    // ── createTroubleTicket ───────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class Create {

        @ParameterizedTest(name = "status=''{0}'' is rejected")
        @ValueSource(strings = {"closed", "acknowledged", "inProgress", "resolved", "NEW", "New", " new"})
        @DisplayName("rejects any status other than exactly 'new'")
        void create_invalidStatus_throws(String status) {
            assertThatThrownBy(() -> service.createTroubleTicket(createReq("EXT-1", status), "t1", null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("niedozwoloną wartość");
        }

        @Test
        @DisplayName("new ticket: isNew=true, ticket saved, response returned")
        void create_newTicket_savesAndReturnsCreated() {
            TroubleTicket saved = ticket("TT-1", "EXT-NEW", "t1");
            TroubleTicketResponse resp = response("TT-1", "EXT-NEW");

            when(repository.findByExternalIdAndTenantId("EXT-NEW", "t1")).thenReturn(Optional.empty());
            when(repository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(resp);

            TroubleTicketService.CreateResult result = service.createTroubleTicket(createReq("EXT-NEW", "new"), "t1", null);

            assertThat(result.isNew()).isTrue();
            assertThat(result.ticket().id()).isEqualTo("TT-1");
            verify(repository).save(any(TroubleTicket.class));
        }

        @Test
        @DisplayName("duplicate externalId: isNew=false, no INSERT, existing ticket returned")
        void create_duplicateExternalId_returnsExistingWithoutInsert() {
            TroubleTicket existing = ticket("TT-EXIST", "EXT-DUP", "t1");
            TroubleTicketResponse resp = response("TT-EXIST", "EXT-DUP");

            when(repository.findByExternalIdAndTenantId("EXT-DUP", "t1")).thenReturn(Optional.of(existing));
            when(mapper.toResponse(existing)).thenReturn(resp);

            TroubleTicketService.CreateResult result = service.createTroubleTicket(createReq("EXT-DUP", "new"), "t1", null);

            assertThat(result.isNew()).isFalse();
            assertThat(result.ticket().id()).isEqualTo("TT-EXIST");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("tenant isolation: same externalId for different tenants creates two separate tickets")
        void create_sameExternalId_differentTenants_bothCreated() {
            TroubleTicket ticketA = ticket("TT-A", "EXT-1", "tenant-A");
            TroubleTicket ticketB = ticket("TT-B", "EXT-1", "tenant-B");

            when(repository.findByExternalIdAndTenantId("EXT-1", "tenant-A")).thenReturn(Optional.empty());
            when(repository.findByExternalIdAndTenantId("EXT-1", "tenant-B")).thenReturn(Optional.empty());
            when(repository.save(any())).thenReturn(ticketA).thenReturn(ticketB);
            when(mapper.toResponse(ticketA)).thenReturn(response("TT-A", "EXT-1"));
            when(mapper.toResponse(ticketB)).thenReturn(response("TT-B", "EXT-1"));

            TroubleTicketService.CreateResult resultA = service.createTroubleTicket(createReq("EXT-1", "new"), "tenant-A", null);
            TroubleTicketService.CreateResult resultB = service.createTroubleTicket(createReq("EXT-1", "new"), "tenant-B", null);

            assertThat(resultA.isNew()).isTrue();
            assertThat(resultB.isNew()).isTrue();
            assertThat(resultA.ticket().id()).isNotEqualTo(resultB.ticket().id());
        }

        @Test
        @DisplayName("DB race condition: DataIntegrityViolationException → returns winning row gracefully")
        void create_dbRaceCondition_returnsExistingOnConstraintViolation() {
            TroubleTicket winner = ticket("TT-WINNER", "EXT-RACE", "t1");
            TroubleTicketResponse resp = response("TT-WINNER", "EXT-RACE");

            when(repository.findByExternalIdAndTenantId("EXT-RACE", "t1")).thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));
            when(repository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));
            when(mapper.toResponse(winner)).thenReturn(resp);

            TroubleTicketService.CreateResult result = service.createTroubleTicket(createReq("EXT-RACE", "new"), "t1", null);

            assertThat(result.isNew()).isFalse();
            assertThat(result.ticket().id()).isEqualTo("TT-WINNER");
        }

        @Test
        @DisplayName("Redis idempotency hit: returns cached ticket without touching DB")
        void create_redisHit_returnsCachedTicketWithoutDbQuery() {
            TroubleTicket existing = ticket("TT-CACHED", "EXT-X", "t1");
            TroubleTicketResponse resp = response("TT-CACHED", "EXT-X");

            when(idempotencyService.get("idem-key-1")).thenReturn(Optional.of("TT-CACHED"));
            when(repository.findByIdAndTenantId("TT-CACHED", "t1")).thenReturn(Optional.of(existing));
            when(mapper.toResponse(existing)).thenReturn(resp);

            TroubleTicketService.CreateResult result = service.createTroubleTicket(createReq("EXT-X", "new"), "t1", "idem-key-1");

            assertThat(result.isNew()).isFalse();
            assertThat(result.ticket().id()).isEqualTo("TT-CACHED");
            verify(repository, never()).findByExternalIdAndTenantId(anyString(), anyString());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Redis returns IN_PROGRESS: falls through to DB path")
        void create_redisInProgress_fallsThroughToDb() {
            TroubleTicket saved = ticket("TT-NEW", "EXT-Y", "t1");
            TroubleTicketResponse resp = response("TT-NEW", "EXT-Y");

            when(idempotencyService.get("idem-key-2")).thenReturn(Optional.of("IN_PROGRESS"));
            when(idempotencyService.claim("idem-key-2")).thenReturn(true);
            when(repository.findByExternalIdAndTenantId("EXT-Y", "t1")).thenReturn(Optional.empty());
            when(repository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(resp);

            TroubleTicketService.CreateResult result = service.createTroubleTicket(createReq("EXT-Y", "new"), "t1", "idem-key-2");

            assertThat(result.isNew()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("null/empty/blank idempotency key: skips Redis, uses DB path only")
        void create_blankIdempotencyKey_skipsRedis(String key) {
            TroubleTicket saved = ticket("TT-Z", "EXT-Z", "t1");
            when(repository.findByExternalIdAndTenantId("EXT-Z", "t1")).thenReturn(Optional.empty());
            when(repository.save(any())).thenReturn(saved);
            when(mapper.toResponse(saved)).thenReturn(response("TT-Z", "EXT-Z"));

            service.createTroubleTicket(createReq("EXT-Z", "new"), "t1", key);

            verifyNoInteractions(idempotencyService);
        }

        @Test
        @DisplayName("generated ticket ID has TT- prefix and is unique per call")
        void create_generatedId_hasPrefixAndIsUnique() {
            when(repository.findByExternalIdAndTenantId(anyString(), anyString())).thenReturn(Optional.empty());

            TroubleTicket t1 = ticket("__dynamic__", "E1", "t1");
            TroubleTicket t2 = ticket("__dynamic__", "E2", "t1");

            when(repository.save(any()))
                    .thenAnswer(inv -> {
                        TroubleTicket t = inv.getArgument(0);
                        return t;
                    });
            when(mapper.toResponse(any())).thenAnswer(inv -> {
                TroubleTicket t = inv.getArgument(0);
                return response(t.getId(), t.getExternalId());
            });

            TroubleTicketService.CreateResult r1 = service.createTroubleTicket(createReq("E1", "new"), "t1", null);
            TroubleTicketService.CreateResult r2 = service.createTroubleTicket(createReq("E2", "new"), "t1", null);

            assertThat(r1.ticket().id()).startsWith("TT-");
            assertThat(r2.ticket().id()).startsWith("TT-");
            assertThat(r1.ticket().id()).isNotEqualTo(r2.ticket().id());
        }
    }

    // ── closeTroubleTicket ────────────────────────────────────────────────────

    @Nested
    @DisplayName("close")
    class Close {

        @ParameterizedTest(name = "status=''{0}'' rejected")
        @ValueSource(strings = {"new", "acknowledged", "inProgress", "resolved", "CLOSED", "Closed", ""})
        @DisplayName("rejects any status other than exactly 'closed'")
        void close_invalidStatus_throws(String status) {
            assertThatThrownBy(() -> service.closeTroubleTicket("TT-1", new TroubleTicketCloseStatusRequest(status), "t1"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("success: ticket status set to CLOSED, saved, response returned")
        void close_existingTicket_setsStatusClosed() {
            TroubleTicket t = ticket("TT-1", "EXT-1", "t1");
            TroubleTicketResponse resp = new TroubleTicketResponse("TT-1", "EXT-1", 1L, "desc", "closed", List.of());

            when(repository.findByIdAndTenantId("TT-1", "t1")).thenReturn(Optional.of(t));
            when(repository.save(t)).thenReturn(t);
            when(mapper.toResponse(t)).thenReturn(resp);

            TroubleTicketResponse result = service.closeTroubleTicket("TT-1", new TroubleTicketCloseStatusRequest("closed"), "t1");

            assertThat(result.status()).isEqualTo("closed");
            assertThat(t.getStatus()).isEqualTo(TroubleTicketStatus.CLOSED);
            verify(repository).save(t);
        }

        @Test
        @DisplayName("ticket not found: throws 404 with correct error code")
        void close_notFound_throws404() {
            when(repository.findByIdAndTenantId("TT-MISS", "t1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.closeTroubleTicket("TT-MISS", new TroubleTicketCloseStatusRequest("closed"), "t1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo(TroubleTicketService.TICKET_NOT_FOUND_CODE));
        }

        @Test
        @DisplayName("cross-tenant: tenant-B cannot close ticket of tenant-A (gets 404)")
        void close_wrongTenant_throws404() {
            when(repository.findByIdAndTenantId("TT-A", "tenant-B")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.closeTroubleTicket("TT-A", new TroubleTicketCloseStatusRequest("closed"), "tenant-B"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository).findByIdAndTenantId("TT-A", "tenant-B");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("already closed ticket: can be closed again (idempotent close)")
        void close_alreadyClosed_doesNotThrow() {
            TroubleTicket t = ticket("TT-1", "EXT-1", "t1");
            t.setStatus(TroubleTicketStatus.CLOSED);
            TroubleTicketResponse resp = new TroubleTicketResponse("TT-1", "EXT-1", 1L, "desc", "closed", List.of());

            when(repository.findByIdAndTenantId("TT-1", "t1")).thenReturn(Optional.of(t));
            when(repository.save(t)).thenReturn(t);
            when(mapper.toResponse(t)).thenReturn(resp);

            TroubleTicketResponse result = service.closeTroubleTicket("TT-1", new TroubleTicketCloseStatusRequest("closed"), "t1");

            assertThat(result.status()).isEqualTo("closed");
        }
    }

    // ── getTroubleTicketById ──────────────────────────────────────────────────

    @Nested
    @DisplayName("get by id")
    class GetById {

        @Test
        @DisplayName("success: returns mapped response")
        void get_exists_returnsResponse() {
            TroubleTicket t = ticket("TT-1", "EXT-1", "t1");
            TroubleTicketResponse resp = response("TT-1", "EXT-1");

            when(repository.findByIdAndTenantId("TT-1", "t1")).thenReturn(Optional.of(t));
            when(mapper.toResponse(t)).thenReturn(resp);

            TroubleTicketResponse result = service.getTroubleTicketById("TT-1", "t1");

            assertThat(result.id()).isEqualTo("TT-1");
        }

        @Test
        @DisplayName("not found: throws ResourceNotFoundException with correct code")
        void get_notFound_throws() {
            when(repository.findByIdAndTenantId("TT-X", "t1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTroubleTicketById("TT-X", "t1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo(TroubleTicketService.TICKET_NOT_FOUND_CODE));
        }

        @Test
        @DisplayName("cross-tenant: returns 404, not 403 — prevents IDOR enumeration")
        void get_wrongTenant_returns404NotForbidden() {
            when(repository.findByIdAndTenantId("TT-A", "tenant-B")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTroubleTicketById("TT-A", "tenant-B"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("repository called with exact tenant scope — no leaked data")
        void get_callsRepositoryWithCorrectTenantScope() {
            when(repository.findByIdAndTenantId("TT-1", "tenant-X")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTroubleTicketById("TT-1", "tenant-X"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository).findByIdAndTenantId("TT-1", "tenant-X");
            verify(repository, never()).findByIdAndTenantId("TT-1", "tenant-Y");
        }
    }

    // ── listTroubleTickets ────────────────────────────────────────────────────

    @Nested
    @DisplayName("list")
    class List_ {

        @Test
        @DisplayName("returns mapped summaries for all tenant tickets")
        void list_returnsMappedSummaries() {
            TroubleTicket t1 = ticket("TT-1", "E1", "t1");
            TroubleTicket t2 = ticket("TT-2", "E2", "t1");
            TroubleTicketSummary s1 = new TroubleTicketSummary("E1", 1L, "desc", "acknowledged");
            TroubleTicketSummary s2 = new TroubleTicketSummary("E2", 1L, "desc", "acknowledged");

            when(repository.findByTenantIdPaged(eq("t1"), any())).thenReturn(java.util.List.of(t1, t2));
            when(mapper.toSummary(t1)).thenReturn(s1);
            when(mapper.toSummary(t2)).thenReturn(s2);

            java.util.List<TroubleTicketSummary> result = service.listTroubleTickets("t1");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TroubleTicketSummary::externalId).containsExactly("E1", "E2");
        }

        @Test
        @DisplayName("returns empty list when tenant has no tickets")
        void list_noTickets_returnsEmpty() {
            when(repository.findByTenantIdPaged(eq("t1"), any())).thenReturn(java.util.List.of());

            java.util.List<TroubleTicketSummary> result = service.listTroubleTickets("t1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("tenant isolation: list only calls repository with correct tenantId")
        void list_tenantIsolation_onlyQueryCorrectTenant() {
            when(repository.findByTenantIdPaged(eq("tenant-A"), any())).thenReturn(java.util.List.of());

            service.listTroubleTickets("tenant-A");

            verify(repository).findByTenantIdPaged(eq("tenant-A"), any());
            verify(repository, never()).findByTenantIdPaged(eq("tenant-B"), any());
        }
    }

    // ── addNote ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addNote")
    class AddNote {

        @Test
        @DisplayName("success: note added to ticket, NoteResponse returned with NOTE- prefix")
        void addNote_success_returnsNoteResponse() {
            TroubleTicket t = ticket("TT-1", "EXT-1", "t1");
            NoteResponse noteResp = new NoteResponse("NOTE-ABCDEF123456", "tekst", LocalDateTime.now());

            when(repository.findByIdAndTenantId("TT-1", "t1")).thenReturn(Optional.of(t));
            when(repository.save(t)).thenReturn(t);
            when(mapper.toNoteResponse(any(Note.class))).thenReturn(noteResp);

            NoteResponse result = service.addNote("TT-1", new NoteCreateRequest("tekst"), "t1");

            assertThat(result.id()).startsWith("NOTE-");
            assertThat(result.text()).isEqualTo("tekst");
            verify(repository).save(t);
        }

        @Test
        @DisplayName("ticket not found: throws ResourceNotFoundException, no save")
        void addNote_ticketNotFound_throws() {
            when(repository.findByIdAndTenantId("TT-MISS", "t1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addNote("TT-MISS", new NoteCreateRequest("text"), "t1"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                            .isEqualTo(TroubleTicketService.TICKET_NOT_FOUND_CODE));

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("cross-tenant: cannot add note to another tenant's ticket")
        void addNote_wrongTenant_throws404() {
            when(repository.findByIdAndTenantId("TT-A", "tenant-B")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addNote("TT-A", new NoteCreateRequest("note"), "tenant-B"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("multiple notes on same ticket: each gets unique NOTE- id")
        void addNote_multipleNotes_uniqueIds() {
            TroubleTicket t = ticket("TT-1", "EXT-1", "t1");

            when(repository.findByIdAndTenantId("TT-1", "t1")).thenReturn(Optional.of(t));
            when(repository.save(any())).thenReturn(t);
            when(mapper.toNoteResponse(any(Note.class))).thenAnswer(inv -> {
                Note n = inv.getArgument(0);
                return new NoteResponse(n.getId(), n.getText(), LocalDateTime.now());
            });

            NoteResponse n1 = service.addNote("TT-1", new NoteCreateRequest("first"), "t1");
            NoteResponse n2 = service.addNote("TT-1", new NoteCreateRequest("second"), "t1");

            assertThat(n1.id()).startsWith("NOTE-");
            assertThat(n2.id()).startsWith("NOTE-");
            assertThat(n1.id()).isNotEqualTo(n2.id());
        }
    }

    // ── error codes ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("error codes")
    class ErrorCodes {

        @Test
        @DisplayName("TICKET_NOT_FOUND_CODE constant matches expected API contract value")
        void errorCode_constant_matchesContract() {
            assertThat(TroubleTicketService.TICKET_NOT_FOUND_CODE)
                    .isEqualTo("TROUBLE_TICKET_NOT_FOUND");
        }

        @Test
        @DisplayName("ResourceNotFoundException message contains Polish text (not null)")
        void errorMessage_isPolishAndNotNull() {
            when(repository.findByIdAndTenantId("X", "t")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTroubleTicketById("X", "t"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Zgłoszenie");
        }
    }
}
