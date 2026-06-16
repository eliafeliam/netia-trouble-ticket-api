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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Core business logic for Trouble Ticket operations.
 *
 * WHY @Transactional at class level + readOnly overrides on reads:
 *   Default @Transactional covers all write methods. readOnly = true on queries tells
 *   Hibernate to skip dirty-checking (faster flush) and allows routing to a read
 *   replica when one is available.
 */
@Slf4j
@Service
@Transactional
public class TroubleTicketService {

    /**
     * Result wrapper for createTroubleTicket.
     *
     * WHY a record (not a boolean return or a separate flag field):
     *   The controller must return HTTP 201 for new tickets and 200 for idempotent
     *   returns. A record makes the intent explicit, is immutable, and avoids the
     *   TOCTOU race that a separate existence check would introduce.
     */
    public record CreateResult(TroubleTicketResponse ticket, boolean isNew) {}

    // WHY constants: single source of truth for error codes/messages.
    // Service tests reference these constants directly — a typo is caught at compile time.
    static final String TICKET_NOT_FOUND_CODE = "TROUBLE_TICKET_NOT_FOUND";
    static final String TICKET_NOT_FOUND_MSG  =
            "Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika.";

    private final TroubleTicketRepository repository;
    private final TroubleTicketMapper     mapper;

    /**
     * WHY @Lazy on IdempotencyService:
     *   IdempotencyService → StringRedisTemplate, created early in the context.
     *   @Lazy defers proxy creation to first use, avoiding circular initialisation
     *   order — the idiomatic alternative to the ApplicationContextHolder anti-pattern.
     */
    private final IdempotencyService idempotencyService;

    public TroubleTicketService(
            TroubleTicketRepository repository,
            TroubleTicketMapper mapper,
            @Lazy IdempotencyService idempotencyService) {
        this.repository         = repository;
        this.mapper             = mapper;
        this.idempotencyService = idempotencyService;
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * WHY Optional.map().orElseThrow() (not isPresent() + get()):
     *   The functional chain is more expressive: "if found, map to DTO; otherwise throw."
     *   It eliminates the intermediate variable and the risk of calling get() without
     *   a prior isPresent() check.
     *
     * WHY 404 for cross-tenant access (not 403):
     *   403 would confirm the resource exists — information disclosure enabling IDOR
     *   enumeration. 404 treats "missing" and "not in your scope" identically.
     */
    @Transactional(readOnly = true)
    public TroubleTicketResponse getTroubleTicketById(String id, String tenantId) {
        log.debug("Fetching ticket id={} tenant={}", id, tenantId);
        return repository.findByIdAndTenantId(id, tenantId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(TICKET_NOT_FOUND_CODE, TICKET_NOT_FOUND_MSG));
    }

    /**
     * WHY server-side hard cap of 500:
     *   The v1 contract returns a plain array with no pagination parameters. Without a cap,
     *   a large tenant would cause OutOfMemoryError. 500 covers the "recent tickets" window.
     *   v2 will introduce cursor-based pagination.
     */
    @Transactional(readOnly = true)
    public List<TroubleTicketSummary> listTroubleTickets(String tenantId) {
        log.debug("Listing tickets for tenant={}", tenantId);
        return repository.findByTenantIdPaged(tenantId, TroubleTicketRepository.top500())
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Creates a new ticket or returns the existing one (idempotent).
     *
     * WHY two-layer idempotency:
     *   Layer 1 — Redis (fast, distributed): X-Idempotency-Key → atomic SETNX.
     *     Caches the finished ticketId so retries skip the DB entirely.
     *   Layer 2 — DB UNIQUE constraint on (tenant_id, external_id):
     *     Safety net for Redis downtime or concurrent slip-through race conditions.
     *     DataIntegrityViolationException → graceful fallback to the winning row.
     *
     * WHY externalId as idempotency key (not internalId):
     *   internalId is generated server-side after insertion — the client cannot know
     *   it before the first successful response. externalId is client-owned and present
     *   in every retry.
     *
     * WHY status "acknowledged" on create:
     *   The contract: "odpowiedź może już zwracać status acknowledged zgodnie z
     *   przepływem przetwarzania po stronie SOZ." We simulate immediate SOZ handoff.
     */
    public CreateResult createTroubleTicket(
            TroubleTicketCreateRequest request, String tenantId, String idempotencyKey) {

        log.debug("Creating ticket externalId={} tenant={}", request.externalId(), tenantId);

        if (!"new".equals(request.status())) {
            throw new ValidationException("Pole status ma niedozwoloną wartość dla tej operacji.");
        }

        // ── Layer 1: Redis fast path (only when header provided) ──────────────
        if (hasIdempotencyKey(idempotencyKey)) {
            // WHY Optional.filter().map().orElseGet():
            //   If the key exists AND has a finished (non IN_PROGRESS) value → return cached.
            //   Otherwise fall through to claim + DB path. Avoids nested if/else blocks.
            return idempotencyService.get(idempotencyKey)
                    .filter(v -> !"IN_PROGRESS".equals(v))
                    .map(ticketId -> new CreateResult(getTroubleTicketById(ticketId, tenantId), false))
                    .orElseGet(() -> claimAndProceed(request, tenantId, idempotencyKey));
        }

        // ── Layer 2: DB-level idempotency (no Redis key provided) ─────────────
        return createOrReturnExisting(request, tenantId, idempotencyKey);
    }

    /**
     * WHY only "closed" is accepted:
     *   The contract restricts public clients to this single transition.
     *   All other transitions (acknowledged → inProgress etc.) are internal SOZ operations.
     *
     * WHY Optional.map().orElseThrow() pattern:
     *   Reads as: "find the ticket, mutate it, save, return DTO — or throw 404."
     *   Eliminates the intermediate ticket variable and the separate isPresent() check.
     */
    public TroubleTicketResponse closeTroubleTicket(
            String id, TroubleTicketCloseStatusRequest request, String tenantId) {

        log.debug("Closing ticket id={} tenant={}", id, tenantId);

        if (!"closed".equals(request.status())) {
            throw new ValidationException("Pole status ma niedozwoloną wartość dla tej operacji.");
        }

        return repository.findByIdAndTenantId(id, tenantId)
                .map(ticket -> {
                    ticket.setStatus(TroubleTicketStatus.CLOSED);
                    TroubleTicket saved = repository.save(ticket);
                    log.info("Ticket closed id={} tenant={}", id, tenantId);
                    return mapper.toResponse(saved);
                })
                .orElseThrow(() -> new ResourceNotFoundException(TICKET_NOT_FOUND_CODE, TICKET_NOT_FOUND_MSG));
    }

    public NoteResponse addNote(String ticketId, NoteCreateRequest request, String tenantId) {
        log.debug("Adding note to ticket id={} tenant={}", ticketId, tenantId);

        return repository.findByIdAndTenantId(ticketId, tenantId)
                .map(ticket -> {
                    Note note = Note.builder()
                            .id(generateNoteId())
                            .text(request.text())
                            .build();
                    ticket.addNote(note);
                    repository.save(ticket);
                    log.info("Note added to ticket id={} tenant={}", ticketId, tenantId);
                    return mapper.toNoteResponse(note);
                })
                .orElseThrow(() -> new ResourceNotFoundException(TICKET_NOT_FOUND_CODE, TICKET_NOT_FOUND_MSG));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Attempts to claim the Redis key; on failure backs off and re-checks. */
    private CreateResult claimAndProceed(
            TroubleTicketCreateRequest request, String tenantId, String idempotencyKey) {

        if (!idempotencyService.claim(idempotencyKey)) {
            // Another replica is processing — brief back-off then check for the result.
            try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            return idempotencyService.get(idempotencyKey)
                    .filter(v -> !"IN_PROGRESS".equals(v))
                    .map(ticketId -> new CreateResult(getTroubleTicketById(ticketId, tenantId), false))
                    .orElseGet(() -> createOrReturnExisting(request, tenantId, idempotencyKey));
        }
        return createOrReturnExisting(request, tenantId, idempotencyKey);
    }

    /** DB-level idempotency: return existing ticket or insert a new one. */
    private CreateResult createOrReturnExisting(
            TroubleTicketCreateRequest request, String tenantId, String idempotencyKey) {

        // WHY Optional.map().orElseGet(): if ticket exists → wrap and return;
        // otherwise → proceed to insertNewTicket. No if/else, no intermediate variable.
        return repository.findByExternalIdAndTenantId(request.externalId(), tenantId)
                .map(existing -> {
                    log.info("Ticket already exists externalId={} tenant={}", request.externalId(), tenantId);
                    storeIdempotencyResult(idempotencyKey, existing.getId(), tenantId);
                    return new CreateResult(mapper.toResponse(existing), false);
                })
                .orElseGet(() -> insertNewTicket(request, tenantId, idempotencyKey));
    }

    /** Performs the actual INSERT, recovering gracefully on a concurrent-insert race. */
    private CreateResult insertNewTicket(
            TroubleTicketCreateRequest request, String tenantId, String idempotencyKey) {

        TroubleTicket ticket = TroubleTicket.builder()
                .id(generateTicketId())
                .tenantId(tenantId)
                .externalId(request.externalId())
                .serviceId(request.serviceId())
                .description(request.description())
                .status(TroubleTicketStatus.ACKNOWLEDGED)
                .build();

        ticket.addNote(Note.builder()
                .id(generateNoteId())
                .text(request.note())
                .build());

        try {
            TroubleTicket saved = repository.save(ticket);
            log.info("Ticket created id={} tenant={}", saved.getId(), tenantId);
            storeIdempotencyResult(idempotencyKey, saved.getId(), tenantId);
            return new CreateResult(mapper.toResponse(saved), true);
        } catch (DataIntegrityViolationException ex) {
            // Another replica won the race and committed first — recover gracefully.
            log.warn("Concurrent duplicate externalId={} tenant={} — returning existing",
                    request.externalId(), tenantId);
            TroubleTicket race = repository.findByExternalIdAndTenantId(request.externalId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Failed to create or fetch existing ticket", ex));
            storeIdempotencyResult(idempotencyKey, race.getId(), tenantId);
            return new CreateResult(mapper.toResponse(race), false);
        }
    }

    private void storeIdempotencyResult(String key, String ticketId, String tenantId) {
        if (hasIdempotencyKey(key)) {
            try {
                idempotencyService.storeResult(key, ticketId);
            } catch (Exception e) {
                // Non-critical — Layer 2 DB constraint covers any retry.
                log.warn("Failed to store idempotency result key={} tenant={}", key, tenantId);
            }
        }
    }

    /** WHY extracted: the null+blank check appears in 3 places — DRY principle. */
    private boolean hasIdempotencyKey(String key) {
        return key != null && !key.isBlank();
    }

    /**
     * WHY time-based prefix + UUID suffix (not sequential int):
     *   Sequential IDs are enumerable (/tickets/1, /tickets/2 …).
     *   The TT- prefix makes the resource type instantly recognisable in logs.
     *   WHY replace("-","").substring() on UUID: removes dashes first so the
     *   8-char suffix is drawn from the full hex alphabet, not mixed with dashes.
     */
    private String generateTicketId() {
        return "TT-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String generateNoteId() {
        return "NOTE-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
