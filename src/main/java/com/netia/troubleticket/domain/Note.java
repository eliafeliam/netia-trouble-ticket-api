package com.netia.troubleticket.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity for a note attached to a TroubleTicket.
 *
 * WHY dual-key design (pk + id) — same rationale as TroubleTicket:
 *   pk (BIGINT) — internal FK used in the trouble_ticket_pk join column. Sequential
 *       integers give the best B-tree index performance for foreign key lookups.
 *   id (VARCHAR) — public note identifier exposed through the API (format: NOTE-<uuid>).
 *       Not guessable, safe to expose in responses.
 *
 * WHY @ManyToOne(fetch = LAZY):
 *   When loading a single note (e.g. after addNote), we don't need to re-fetch the
 *   entire parent ticket. LAZY avoids that extra JOIN. The parent ticket is already
 *   in the Hibernate session when addNote() is called, so no extra query occurs.
 *
 * WHY @ToString.Exclude + @EqualsAndHashCode.Exclude on troubleTicket:
 *   Lombok's @Data generates toString/equals/hashCode using all fields by default.
 *   Including the parent ticket in toString() causes infinite recursion
 *   (Note → TroubleTicket → List<Note> → Note → ...). Excluding it breaks the cycle.
 *
 * WHY @CreationTimestamp (not manually setting createdAt):
 *   Hibernate sets this automatically at INSERT time — one less thing for service code
 *   to manage, and it uses the DB server time consistent with other timestamps.
 */
@Entity
@Table(name = "note")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Note {

    /** Internal surrogate PK — used only for FK joins, never exposed in the API. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pk;

    /** Public note identifier exposed in API responses. */
    @Column(name = "id", unique = true, nullable = false, length = 50)
    private String id;

    /**
     * Back-reference to the parent ticket.
     * LAZY: parent ticket is not loaded when querying notes in isolation.
     * Excluded from Lombok toString/equals to prevent infinite recursion.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trouble_ticket_pk", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TroubleTicket troubleTicket;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
