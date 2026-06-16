package com.netia.common.security.rls;

import com.netia.common.security.TenantAwarePrincipal;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Sets PostgreSQL RLS tenant context before each repository method execution.
 * Uses a native query to execute SET LOCAL as a separate statement,
 * avoiding the JDBC "No results were returned" error that occurs when
 * SET LOCAL is concatenated with a SELECT in StatementInspector.
 */
@Slf4j
@Aspect
@Component
public class TenantContextAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.netia.troubleticket.repository.*.*(..))")
    public void setTenantContext() {
        String tenantId = extractTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            entityManager.createNativeQuery("SET LOCAL app.current_tenant = '" + escape(tenantId) + "'")
                    .executeUpdate();
            log.debug("RLS tenant context set: {}", tenantId);
        }
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

    private String escape(String tenantId) {
        return tenantId.replace("'", "''");
    }
}
