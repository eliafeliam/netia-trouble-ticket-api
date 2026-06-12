package com.netia.troubleticket.service;

import com.netia.common.exception.ResourceNotFoundException;
import com.netia.common.exception.ValidationException;
import com.netia.troubleticket.domain.Note;
import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.dto.*;
import com.netia.troubleticket.mapper.TroubleTicketMapper;
import com.netia.troubleticket.repository.TroubleTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TroubleTicketService {

    private final TroubleTicketRepository repository;
    private final TroubleTicketMapper mapper;

    @Transactional(readOnly = true)
    public TroubleTicketResponse getTroubleTicketById(String id, String tenantId) {
        log.debug("Fetching trouble ticket: {} for tenant: {}", id, tenantId);

        TroubleTicket ticket = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TROUBLE_TICKET_NOT_FOUND",
                        "Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika."
                ));

        return mapper.toResponse(ticket);
    }

    /**
     * Creates a new trouble ticket or returns existing one based on idempotency key (tenantId, externalId).
     * New tickets are created with status 'acknowledged' (simulating backend processing).
     */
    public TroubleTicketResponse createTroubleTicket(TroubleTicketCreateRequest request, String tenantId, String idempotencyKey) {
        log.debug("Creating trouble ticket with externalId: {} for tenant: {}", request.getExternalId(), tenantId);

        // Validate status - only 'new' is allowed
        if (!"new".equals(request.getStatus())) {
            throw new ValidationException("Pole status ma niedozwoloną wartość dla tej operacji.");
        }

        // Check for existing ticket (idempotency by externalId)
        Optional<TroubleTicket> existingTicket = repository.findByExternalIdAndTenantId(
                request.getExternalId(),
                tenantId
        );

        if (existingTicket.isPresent()) {
            log.info("Trouble ticket already exists: {} for tenant: {}", request.getExternalId(), tenantId);
            return mapper.toResponse(existingTicket.get());
        }

        // If idempotencyKey provided, try to claim via IdempotencyService
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                // lazy autowiring via Spring context to avoid circular dependency in constructors
                if (idempotencyService == null) {
                    idempotencyService = ApplicationContextHolder.getBean(com.netia.common.idempotency.IdempotencyService.class);
                }

                // If a stored result exists, return it
                Optional<String> stored = idempotencyService.get(idempotencyKey);
                if (stored.isPresent()) {
                    String ticketId = stored.get();
                    return getTroubleTicketById(ticketId, tenantId);
                }

                // Try to claim the key for processing
                boolean claimed = idempotencyService.claim(idempotencyKey);
                if (!claimed) {
                    // Another request is processing; wait briefly and try to fetch stored result
                    Thread.sleep(250);
                    Optional<String> stored2 = idempotencyService.get(idempotencyKey);
                    if (stored2.isPresent()) {
                        return getTroubleTicketById(stored2.get(), tenantId);
                    }
                    // else continue without idempotency guarantee
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Create new ticket
        TroubleTicket ticket = TroubleTicket.builder()
                .id(generateTicketId())
                .tenantId(tenantId)
                .externalId(request.getExternalId())
                .serviceId(request.getServiceId())
                .description(request.getDescription())
                .status(TroubleTicketStatus.acknowledged) // Newly created tickets are acknowledged
                .build();

        // Add initial note
        Note initialNote = Note.builder()
                .id(generateNoteId())
                .text(request.getNote())
                .createdAt(LocalDateTime.now())
                .build();
        ticket.addNote(initialNote);

        try {
            TroubleTicket saved = repository.save(ticket);
            log.info("Trouble ticket created: {} for tenant: {}", ticket.getId(), tenantId);

            // store idempotency result if key provided
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (idempotencyService == null) {
                    idempotencyService = ApplicationContextHolder.getBean(com.netia.common.idempotency.IdempotencyService.class);
                }
                try {
                    idempotencyService.storeResult(idempotencyKey, saved.getId());
                } catch (Exception ignore) {
                    log.warn("Failed to store idempotency result for key={}", idempotencyKey);
                }
            }

            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Possible race: another request created the same (tenantId, externalId).
            log.warn("DataIntegrityViolation when creating ticket (possible concurrent create), fetching existing: {} for tenant: {}", request.getExternalId(), tenantId);
            TroubleTicket existing = repository.findByExternalIdAndTenantId(request.getExternalId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Failed to create or fetch existing trouble ticket", ex));

            // store idempotency mapping if key present
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (idempotencyService == null) {
                    idempotencyService = ApplicationContextHolder.getBean(com.netia.common.idempotency.IdempotencyService.class);
                }
                try {
                    idempotencyService.storeResult(idempotencyKey, existing.getId());
                } catch (Exception ignore) {
                    log.warn("Failed to store idempotency result for key={} after race", idempotencyKey);
                }
            }

            return mapper.toResponse(existing);
        }
    }

    @Transactional(readOnly = true)
    public List<TroubleTicketSummary> listTroubleTickets(String tenantId) {
        log.debug("Listing trouble tickets for tenant: {}", tenantId);

        List<TroubleTicket> tickets = repository.findAllByTenantId(tenantId);
        return tickets.stream()
                .map(mapper::toSummary)
                .toList();
    }

    public TroubleTicketResponse closeTroubleTicket(String id, TroubleTicketCloseStatusRequest request, String tenantId) {
        log.debug("Closing trouble ticket: {} for tenant: {}", id, tenantId);

        // Validate status - only 'closed' is allowed
        if (!"closed".equals(request.getStatus())) {
            throw new ValidationException("Pole status ma niedozwoloną wartość dla tej operacji.");
        }

        TroubleTicket ticket = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TROUBLE_TICKET_NOT_FOUND",
                        "Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika."
                ));

        ticket.setStatus(TroubleTicketStatus.closed);
        TroubleTicket saved = repository.save(ticket);
        log.info("Trouble ticket closed: {} for tenant: {}", id, tenantId);

        return mapper.toResponse(saved);
    }

    public NoteResponse addNote(String ticketId, NoteCreateRequest request, String tenantId) {
        log.debug("Adding note to trouble ticket: {} for tenant: {}", ticketId, tenantId);

        TroubleTicket ticket = repository.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TROUBLE_TICKET_NOT_FOUND",
                        "Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika."
                ));

        Note note = Note.builder()
                .id(generateNoteId())
                .text(request.getText())
                .createdAt(LocalDateTime.now())
                .build();

        ticket.addNote(note);
        repository.save(ticket);
        log.info("Note added to trouble ticket: {} for tenant: {}", ticketId, tenantId);

        return mapper.toNoteResponse(note);
    }

    private String generateTicketId() {
        return "TT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateNoteId() {
        return "NOTE-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}



