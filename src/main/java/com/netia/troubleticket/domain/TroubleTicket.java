package com.netia.troubleticket.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a Trouble Ticket.
 *
 * WHY dual-key design (pk + id):
 *   pk (BIGINT) — internal surrogate key used for JPA relations and JOIN performance.
 *       Sequential integers are ideal for B-tree index scans on FK joins.
 *   id (VARCHAR) — business / public identifier exposed through the API.
 *       Prefixed, time-based + random (e.g. "TT-1718200000000-A1B2C3D4"). Not guessable,
 *       human-readable in logs, and safe to expose in URLs without leaking row count.
 *
 * WHY @Version (Optimistic Locking):
 *   If two operators simultaneously PATCH the same ticket (e.g., both closing it),
 *   optimistic locking detects the conflict at commit time and throws
 *   ObjectOptimisticLockingFailureException. The global handler maps this to 409 Conflict,
 *   which is far safer than the "last writer wins" behaviour you get without @Version.
 *
 * WHY FetchType.LAZY on notes (not EAGER):
 *   EAGER loading fetches notes for EVERY ticket query — including listTroubleTickets which
 *   only needs summary fields. This is the classic N+1 source. Instead we use LAZY and let
 *   the repository control when notes are loaded via JOIN FETCH / @EntityGraph, only paying
 *   the join cost when the caller actually needs note data (e.g., getTroubleTicketById).
 *
 * WHY TroubleTicketStatusConverter (not @Enumerated):
 *   @Enumerated(EnumType.STRING) stores enum.name() — "NEW", "IN_PROGRESS" etc. (uppercase).
 *   Our contract uses lowercase camelCase ("new", "inProgress"). The converter stores getValue()
 *   so DB values match the API contract exactly, eliminating the need for mapper hacks.
 */
@Entity
@Table(name = "trouble_ticket", indexes = {
        @Index(name = "idx_tenant_external_id", columnList = "tenant_id, external_id", unique = true),
        @Index(name = "idx_tenant_id",          columnList = "tenant_id"),
        @Index(name = "idx_status",             columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TroubleTicket {

    /** Internal surrogate PK — never exposed through the API. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pk;

    /**
     * Optimistic locking version. Incremented by Hibernate on each UPDATE.
     * Concurrent modifications to the same row result in ObjectOptimisticLockingFailureException
     * (mapped to HTTP 409 by the global exception handler) rather than silent data loss.
     */
    @Version
    private Long version;

    /** Public business identifier exposed in the API (format: TT-<timestamp>-<random>). */
    @Column(name = "id", unique = true, nullable = false, length = 50)
    private String id;

    /** Tenant scope from the Bearer JWT. All data access is scoped to this value. */
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    /** Client-side idempotency key — unique per tenant. Forms the idempotency constraint with tenantId. */
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * Stored via TroubleTicketStatusConverter (autoApply = true) so the DB value matches
     * the API contract string (e.g. "inProgress") rather than the Java enum name ("IN_PROGRESS").
     */
    @Column(name = "status", nullable = false, length = 20)
    private TroubleTicketStatus status;

    /**
     * WHY LAZY + cascade ALL:
     *   LAZY: notes are loaded only when explicitly requested (JOIN FETCH in repository queries).
     *   cascade ALL + orphanRemoval: deleting a ticket also deletes its notes (cascade),
     *   and removing a note from the collection deletes it from the DB (orphanRemoval).
     */
    @OneToMany(mappedBy = "troubleTicket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Note> notes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Bidirectional relationship helper — always use this instead of notes.add() directly. */
    public void addNote(Note note) {
        if (this.notes == null) {
            this.notes = new ArrayList<>();
        }
        note.setTroubleTicket(this);
        this.notes.add(note);
    }
}
