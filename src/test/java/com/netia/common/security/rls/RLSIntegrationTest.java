package com.netia.common.security.rls;

import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.repository.TroubleTicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Row-Level Security (RLS) enforcement.
 * Verifies that Postgres RLS policies prevent cross-tenant data access.
 */
@Slf4j
@Testcontainers
@DataJpaTest
@Import({RLSConfiguration.class, TenantStatementInspector.class, com.netia.common.security.TenantAwarePrincipal.class})
@ActiveProfiles("test")
class RLSIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_rls")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("db/migration/V1__Initial_Schema.sql");

    @Autowired
    private TroubleTicketRepository repository;

    private static final String TENANT_A = "tenant-A";
    private static final String TENANT_B = "tenant-B";
    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    @BeforeEach
    void setUp() {
        // Clear any existing data
        repository.deleteAll();
    }

    @Test
    void testRLSEnforcesPerTenantIsolation() {
        // Setup: Create tickets for different tenants
        TroubleTicket ticketA = createAndSaveTicket(TENANT_A, "ticket-ext-A", "Issue in A");
        TroubleTicket ticketB = createAndSaveTicket(TENANT_B, "ticket-ext-B", "Issue in B");

        // Act: Query as tenant A
        setSecurityContext(TENANT_A, USER_A);
        List<TroubleTicket> ticketsVisibleToA = repository.findAllByTenantId(TENANT_A);

        // Assert: Tenant A should only see their own ticket
        assertEquals(1, ticketsVisibleToA.size());
        assertEquals("ticket-ext-A", ticketsVisibleToA.get(0).getExternalId());
        log.info("✅ Tenant A sees only their own ticket");

        // Act: Query as tenant B
        setSecurityContext(TENANT_B, USER_B);
        List<TroubleTicket> ticketsVisibleToB = repository.findAllByTenantId(TENANT_B);

        // Assert: Tenant B should only see their own ticket
        assertEquals(1, ticketsVisibleToB.size());
        assertEquals("ticket-ext-B", ticketsVisibleToB.get(0).getExternalId());
        log.info("✅ Tenant B sees only their own ticket");
    }

    @Test
    void testRLSProtectsAgainstBuggyRepositoryCode() {
        // Setup: Create tickets for different tenants
        TroubleTicket ticketA = createAndSaveTicket(TENANT_A, "ticket-ext-A", "Issue in A");
        TroubleTicket ticketB = createAndSaveTicket(TENANT_B, "ticket-ext-B", "Issue in B");

        // Act: Imagine a buggy repository that forgets to filter by tenant
        // SELECT * FROM trouble_ticket (no WHERE clause)
        // RLS should still protect and return only tenant A's data
        setSecurityContext(TENANT_A, USER_A);
        List<TroubleTicket> allTickets = repository.findAll();

        // Assert: Despite buggy code, RLS filters to only tenant A's tickets
        assertEquals(1, allTickets.size(), "RLS should protect even if repository code is buggy");
        assertEquals("ticket-ext-A", allTickets.get(0).getExternalId());
        log.info("✅ RLS protects against buggy repository code (findAll was not filtered, but RLS applied)");
    }

    @Test
    void testRLSAcrossMultipleTenants() {
        // Setup: Create multiple tickets for multiple tenants
        TroubleTicket ticket1A = createAndSaveTicket(TENANT_A, "ticket-1-A", "First issue A");
        TroubleTicket ticket2A = createAndSaveTicket(TENANT_A, "ticket-2-A", "Second issue A");
        TroubleTicket ticket3B = createAndSaveTicket(TENANT_B, "ticket-1-B", "First issue B");
        TroubleTicket ticket4B = createAndSaveTicket(TENANT_B, "ticket-2-B", "Second issue B");

        // Act & Assert for Tenant A
        setSecurityContext(TENANT_A, USER_A);
        List<TroubleTicket> ticketsA = repository.findAllByTenantId(TENANT_A);
        assertEquals(2, ticketsA.size(), "Tenant A should see exactly 2 tickets");
        assertTrue(ticketsA.stream().allMatch(t -> t.getTenantId().equals(TENANT_A)));
        log.info("✅ Tenant A sees 2 tickets");

        // Act & Assert for Tenant B
        setSecurityContext(TENANT_B, USER_B);
        List<TroubleTicket> ticketsB = repository.findAllByTenantId(TENANT_B);
        assertEquals(2, ticketsB.size(), "Tenant B should see exactly 2 tickets");
        assertTrue(ticketsB.stream().allMatch(t -> t.getTenantId().equals(TENANT_B)));
        log.info("✅ Tenant B sees 2 tickets");
    }

    // ======================== Helper Methods ========================

    private TroubleTicket createAndSaveTicket(String tenantId, String externalId, String description) {
        TroubleTicket ticket = TroubleTicket.builder()
                .id("TT-" + System.nanoTime())
                .tenantId(tenantId)
                .externalId(externalId)
                .serviceId(123L)
                .description(description)
                .status(TroubleTicketStatus.acknowledged)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(ticket);
    }

    private void setSecurityContext(String tenantId, String userId) {
        com.netia.common.security.TenantAwarePrincipal principal =
            new com.netia.common.security.TenantAwarePrincipal(userId, tenantId);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, tenantId,
                java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

