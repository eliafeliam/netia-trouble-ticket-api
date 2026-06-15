package com.netia.common.security.rls;

import com.netia.common.security.TenantAwarePrincipal;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Hibernate StatementInspector that prepends SET LOCAL app.current_tenant before every
 * SQL statement, activating Postgres Row Level Security for the current transaction.
 *
 * HOW RLS works end-to-end:
 *   1. JwtAuthenticationFilter sets a TenantAwarePrincipal in the SecurityContext.
 *   2. This inspector reads tenantId from the SecurityContext (available on the same
 *      virtual thread / platform thread that handles the request).
 *   3. Before every JDBC statement Hibernate calls inspect(sql) — we prefix it with
 *      "SET LOCAL app.current_tenant = '<id>'; " so Postgres sets the GUC variable.
 *   4. The RLS policy USING (tenant_id = current_setting('app.current_tenant', true))
 *      automatically filters every SELECT/INSERT/UPDATE/DELETE to the current tenant.
 *   5. SET LOCAL scope is limited to the current transaction — HikariCP connection
 *      pool reuse is safe because each new transaction resets the GUC.
 *
 * WHY not use a TransactionSynchronization / @Aspect instead:
 *   A @Aspect would need to intercept every repository method individually and could
 *   be bypassed by EntityManager.createNativeQuery(). StatementInspector runs at the
 *   lowest possible layer — it fires for ALL SQL Hibernate generates, including native
 *   queries, making it impossible to forget the tenant context.
 *
 * WHY single-quote escaping is sufficient (not a PreparedStatement):
 *   StatementInspector works at the SQL string level, before JDBC prepares the statement.
 *   We cannot use a bind parameter here. Escaping single quotes ('' in SQL) is the
 *   standard defence against injection in string literals. The tenantId value comes
 *   exclusively from a cryptographically verified JWT claim — it is not user-supplied
 *   free-text — so the risk is minimal. If extra hardening is needed, add a whitelist
 *   regex validation on tenantId in JwtAuthenticationFilter.
 */
@Slf4j
@Component
public class TenantStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        String tenantId = extractTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return sql;
        }
        // SET LOCAL: GUC variable lives only for the duration of the current transaction.
        // Using escaped string literal — see class-level javadoc for SQL injection analysis.
        String setLocal = "SET LOCAL app.current_tenant = '" + escape(tenantId) + "'; ";
        log.debug("RLS tenant context set: {}", tenantId);
        return setLocal + sql;
    }

    private String extractTenantId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof TenantAwarePrincipal principal) {
                return principal.getTenantId();
            }
        } catch (Exception e) {
            log.debug("Could not extract tenant from SecurityContext: {}", e.getMessage());
        }
        return null;
    }

    /** Escapes single quotes in SQL string literals by doubling them (standard SQL escaping). */
    private String escape(String tenantId) {
        return tenantId.replace("'", "''");
    }
}
