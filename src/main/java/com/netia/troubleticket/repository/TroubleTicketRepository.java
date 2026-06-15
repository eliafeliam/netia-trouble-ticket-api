package com.netia.troubleticket.repository;

import com.netia.troubleticket.domain.TroubleTicket;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for TroubleTicket with built-in tenant isolation.
 *
 * WHY every query includes tenantId (defense-in-depth):
 *   Row Level Security (RLS) in Postgres (V2 migration) provides the primary DB-level
 *   isolation. Including tenantId in WHERE clauses is a second layer — even if RLS were
 *   misconfigured or bypassed (e.g. superuser session), the query itself cannot return
 *   another tenant's data. Two independent layers reduce the blast radius of any single failure.
 *
 * WHY @EntityGraph instead of JOIN FETCH for findByIdAndTenantId:
 *   @EntityGraph is the Spring Data idiomatic way to eagerly load associations for a
 *   specific query. It avoids duplicating JOIN FETCH boilerplate in every JPQL query and
 *   lets Hibernate choose the optimal SQL strategy (subselect vs join).
 *   Result: notes are loaded in the same transaction as the ticket — no N+1 problem,
 *   no LazyInitializationException outside the transaction.
 *
 * WHY Pageable for the list query (not method-name Top500):
 *   Spring Data's "findTopN" name convention generates correct SQL LIMIT only for simple
 *   queries. With a JOIN FETCH the generated SQL may not apply the LIMIT correctly and
 *   Hibernate falls back to in-memory filtering (fetching ALL rows then slicing in Java).
 *   Passing an explicit Pageable ensures Postgres applies "LIMIT 500" at the DB level,
 *   protecting against OutOfMemoryError on large tenants.
 */
@Repository
public interface TroubleTicketRepository extends JpaRepository<TroubleTicket, Long> {

    /**
     * Fetches a single ticket with its notes in one query (no N+1).
     *
     * WHY @EntityGraph("TroubleTicket.withNotes"):
     *   Triggers a LEFT JOIN FETCH on the notes collection so the full ticket representation
     *   (including note list) is loaded in a single SQL statement. Without this, accessing
     *   ticket.getNotes() outside the original transaction would throw LazyInitializationException.
     */
    @EntityGraph(attributePaths = "notes")
    Optional<TroubleTicket> findByIdAndTenantId(String id, String tenantId);

    /**
     * Finds ticket by client externalId for idempotency check (Layer 2 deduplication).
     * Also loads notes so the response is consistent with findByIdAndTenantId.
     */
    @EntityGraph(attributePaths = "notes")
    Optional<TroubleTicket> findByExternalIdAndTenantId(String externalId, String tenantId);

    /**
     * Lists tickets for a tenant, most recent first, with a hard DB-level LIMIT via Pageable.
     *
     * WHY no JOIN FETCH here (notes NOT loaded):
     *   The list endpoint returns TroubleTicketSummary which contains only externalId,
     *   serviceId, description, and status — no notes. Loading notes here would be pure
     *   waste (extra JOIN + data transfer). LAZY loading means notes are never touched.
     *
     * WHY Pageable instead of findTop500ByTenantId:
     *   See class-level javadoc. The PageRequest is constructed in the service with a
     *   fixed size of 500, ensuring the LIMIT is pushed to the DB query.
     */
    @Query("SELECT t FROM TroubleTicket t WHERE t.tenantId = :tenantId ORDER BY t.createdAt DESC")
    List<TroubleTicket> findByTenantIdPaged(@Param("tenantId") String tenantId, Pageable pageable);

    /**
     * Convenience factory — creates a Pageable for the top-500 list query.
     * Centralises the limit constant so it can be found and changed in one place.
     */
    static Pageable top500ByCreatedAtDesc() {
        return PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Used by RLSIntegrationTest to assert cross-tenant isolation via findAll(). */
    List<TroubleTicket> findAllByTenantId(String tenantId);
}
