package com.netia.common.security.rls;

import com.netia.AbstractIntegrationTest;
import com.netia.common.security.TenantAwarePrincipal;
import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.repository.TroubleTicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RLS Integration Tests — Postgres tenant isolation")
@org.junit.jupiter.api.Tag("integration")
class RLSIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TroubleTicketRepository repository;

    private static final String TENANT_A = "tenant-A";
    private static final String TENANT_B = "tenant-B";

    @BeforeEach
    void setUp() {
        setSecurityContext(TENANT_A);
        repository.findAllByTenantId(TENANT_A).forEach(repository::delete);
        setSecurityContext(TENANT_B);
        repository.findAllByTenantId(TENANT_B).forEach(repository::delete);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Tenant A sees only its own ticket via explicit tenant query")
    void testRLSEnforcesPerTenantIsolation() {
        setSecurityContext(TENANT_A);
        saveTicket(TENANT_A, "ext-A-" + System.nanoTime(), "Issue in A");

        setSecurityContext(TENANT_B);
        saveTicket(TENANT_B, "ext-B-" + System.nanoTime(), "Issue in B");

        setSecurityContext(TENANT_A);
        List<TroubleTicket> ticketsA = repository.findAllByTenantId(TENANT_A);

        assertEquals(1, ticketsA.size());
        assertTrue(ticketsA.get(0).getExternalId().startsWith("ext-A-"));
        log.info("✅ Tenant A sees only their own ticket");
    }

    @Test
    @DisplayName("RLS blocks cross-tenant data even when repository forgets WHERE clause (findAll)")
    void testRLSProtectsAgainstBuggyRepositoryCode() {
        setSecurityContext(TENANT_A);
        saveTicket(TENANT_A, "ext-A-" + System.nanoTime(), "Issue in A");

        setSecurityContext(TENANT_B);
        saveTicket(TENANT_B, "ext-B-" + System.nanoTime(), "Issue in B");

        setSecurityContext(TENANT_A);
        List<TroubleTicket> all = repository.findAll();

        assertEquals(1, all.size(), "RLS must filter findAll() to current tenant");
        assertTrue(all.get(0).getExternalId().startsWith("ext-A-"));
        log.info("✅ RLS protected against buggy findAll() — returned only tenant-A data");
    }

    @Test
    @DisplayName("Multiple tickets per tenant are visible only to their owner")
    void testRLSAcrossMultipleTenants() {
        long ts = System.nanoTime();
        setSecurityContext(TENANT_A);
        saveTicket(TENANT_A, "ext-A-1-" + ts, "First issue A");
        saveTicket(TENANT_A, "ext-A-2-" + ts, "Second issue A");

        setSecurityContext(TENANT_B);
        saveTicket(TENANT_B, "ext-B-1-" + ts, "First issue B");
        saveTicket(TENANT_B, "ext-B-2-" + ts, "Second issue B");

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

    private void saveTicket(String tenantId, String externalId, String description) {
        TroubleTicket ticket = TroubleTicket.builder()
                .id("TT-" + System.nanoTime())
                .tenantId(tenantId)
                .externalId(externalId)
                .serviceId(123L)
                .description(description)
                .status(TroubleTicketStatus.ACKNOWLEDGED)
                .build();
        repository.saveAndFlush(ticket);
    }

    private void setSecurityContext(String tenantId) {
        var principal = new TenantAwarePrincipal("test-user", tenantId);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, tenantId,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
