package com.netia.common.security.rls;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * Hibernate StatementInspector that sets app.current_tenant for RLS.
 * This is executed before every SQL statement, ensuring tenant isolation at DB level.
 */
@Slf4j
@Component
public class TenantStatementInspector implements StatementInspector {

    /**
     * Called before every SQL execution.
     * Sets the current tenant as a session variable for Postgres RLS.
     */
    @Override
    public String inspect(String sql) {
        String tenantId = extractTenantIdFromContext();
        if (tenantId != null && !tenantId.isBlank()) {
            // SET LOCAL sets the variable only for current transaction
            String setLocalSql = "SET LOCAL app.current_tenant = '" + escapeTenantId(tenantId) + "';";
            log.debug("Setting tenant context for RLS: {}", tenantId);
            return setLocalSql + " " + sql;
        }
        return sql;
    }

    private String extractTenantIdFromContext() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.netia.common.security.TenantAwarePrincipal) {
                com.netia.common.security.TenantAwarePrincipal principal =
                    (com.netia.common.security.TenantAwarePrincipal) auth.getPrincipal();
                return principal.getTenantId();
            }
        } catch (Exception ignore) {
            log.debug("Could not extract tenant from security context");
        }
        return null;
    }

    /**
     * Escape tenant ID to prevent SQL injection (basic protection).
     * In real production, use prepared statements instead.
     */
    private String escapeTenantId(String tenantId) {
        return tenantId.replace("'", "''");
    }
}

