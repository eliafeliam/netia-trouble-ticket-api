package com.netia.common.security.rls;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers TenantStatementInspector with Hibernate.
 *
 * WHY HibernatePropertiesCustomizer (not @Component on TenantStatementInspector):
 *   Hibernate's StatementInspector must be registered via the "hibernate.session_factory.
 *   statement_inspector" property — Hibernate does NOT auto-discover @Component beans.
 *   HibernatePropertiesCustomizer is the Spring Boot idiomatic hook for adding arbitrary
 *   Hibernate properties without touching application.yml or LocalContainerEntityManagerFactoryBean
 *   directly.
 *
 * WHY a separate @Configuration class:
 *   Keeps the registration concern separate from the inspector logic itself, making it
 *   easy to swap the inspector in tests or disable RLS in non-Postgres environments
 *   without touching the inspector class.
 */
@Configuration
public class RLSConfiguration {

    private final TenantStatementInspector inspector;

    public RLSConfiguration(TenantStatementInspector inspector) {
        this.inspector = inspector;
    }

    @Bean
    public HibernatePropertiesCustomizer hibernateRlsCustomizer() {
        // AvailableSettings.STATEMENT_INSPECTOR is the official Hibernate constant for this property.
        // Passing the bean instance (not the class name) means Hibernate uses our Spring-managed
        // bean directly — no reflection, no second instantiation, full testability.
        return props -> props.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }
}
