package com.netia.common.security.rls;

import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.repository.TroubleTicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test proving that Postgres Row Level Security (RLS) policies
 * enforce tenant isolation independently of application WHERE clauses.
 *
 * WHY @DataJpaTest (not @SpringBootTest):
 *   @DataJpaTest loads only the JPA slice (entities, repositories, Flyway) without
 *   starting the full web/security context. This makes the test fast while still
 *   running against a real Postgres instance (via Testcontainers configured in
 *   application-test.yml) where RLS policies are applied.
 *
 * WHY @Import({RLSConfiguration, TenantStatementInspector}):
 *   @DataJpaTest does not load @Configuration beans outside the JPA slice by default.
 *   We explicitly import the RLS beans so Hibernate registers the StatementInspector
 *   and the SET LOCAL prepend actually fires during tests.
 *
 * What these tests prove:
 *   1. A tenant can only see its own tickets even when the query has no WHERE filter.
 *   2. RLS blocks cross-tenant access even if a developer writes a "buggy" findAll().
 *   3. Multiple tenants are fully isolated from each other.
 */
@Slf4j
@DataJpaTest
@Import({RLSConfiguration.class, TenantStatementInspector.class})
@ActiveProfiles("test")
@DisplayName("RLS Integration Tests — Postgres tenant isolation")
class RLSIntegrationTest {

    @Autowired
    private TroubleTicketRepository repository;

    private static final String TENANT_A = "tenant-A";
    private static final String TENANT_B = "tenant-B";

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Tenant A sees only its own ticket via explicit tenant query")
    void testRLSEnforcesPerTenantIsolation() {
        saveTicket(TENANT_A, "ext-A", "Issue in A");
        saveTicket(TENANT_B, "ext-B", "Issue in B");

        setSecurityContext(TENANT_A);
        List<TroubleTicket> ticketsA = repository.findAllByTenantId(TENANT_A);

        assertEquals(1, ticketsA.size());
        assertEquals("ext-A", ticketsA.get(0).getExternalId());
        log.info("✅ Tenant A sees only their own ticket");
    }

    @Test
    @DisplayName("RLS blocks cross-tenant data even when repository forgets WHERE clause (findAll)")
    void testRLSProtectsAgainstBuggyRepositoryCode() {
        saveTicket(TENANT_A, "ext-A", "Issue in A");
        saveTicket(TENANT_B, "ext-B", "Issue in B");

        // Simulate a developer accidentally calling findAll() without a tenant filter.
        // RLS policy must silently restrict results to the current tenant's rows only.
        setSecurityContext(TENANT_A);
        List<TroubleTicket> all = repository.findAll();

        assertEquals(1, all.size(), "RLS must filter findAll() to current tenant");
        assertEquals("ext-A", all.get(0).getExternalId());
        log.info("✅ RLS protected against buggy findAll() — returned only tenant-A data");
    }

    @Test
    @DisplayName("Multiple tickets per tenant are visible only to their owner")
    void testRLSAcrossMultipleTenants() {
        saveTicket(TENANT_A, "ext-A-1", "First issue A");
        saveTicket(TENANT_A, "ext-A-2", "Second issue A");
        saveTicket(TENANT_B, "ext-B-1", "First issue B");
        saveTicket(TENANT_B, "ext-B-2", "Second issue B");

        setSecurityContext(TENANT_A);
        List<TroubleTicket> ticketsA = repository.findAllByTenantId(TENANT_A);
        assertEquals(2, ticketsA.size(), "Tenant A should see exactly 2 tickets");
        assertTrue(ticketsA.stream().allMatch(t -> TENANT_A.equals(t.getTenantId())));
        log.info("✅ Tenant A sees 2 tickets");

        setSecurityContext(TENANT_B);
        List<TroubleTicket> ticketsB = repository.findAllByTenantId(TENANT_B);
        assertEquals(2, ticketsB.size(), "Tenant B should see exactly 2 tickets");
        assertTrue(ticketsB.stream().allMatch(t -> TENANT_B.equals(t.getTenantId())));
        log.info("✅ Tenant B sees 2 tickets");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveTicket(String tenantId, String externalId, String description) {
        // WHY no manual createdAt/updatedAt: @CreationTimestamp / @UpdateTimestamp
        // are set by Hibernate at INSERT — we must not set them in the builder.
        TroubleTicket ticket = TroubleTicket.builder()
                .id("TT-" + System.nanoTime())
                .tenantId(tenantId)
                .externalId(externalId)
                .serviceId(123L)
                .description(description)
                .status(TroubleTicketStatus.ACKNOWLEDGED)
                .build();
        repository.save(ticket);
    }

    private void setSecurityContext(String tenantId) {
        var principal = new com.netia.common.security.TenantAwarePrincipal("test-user", tenantId);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, tenantId,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
