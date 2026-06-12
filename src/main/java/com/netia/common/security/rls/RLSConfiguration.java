package com.netia.common.security.rls;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * Configuration for Row-Level Security (RLS) integration with Hibernate.
 * Registers the TenantStatementInspector to automatically inject SET LOCAL for tenant context.
 */
@Configuration
public class RLSConfiguration {

    /**
     * Register the TenantStatementInspector as a bean so Hibernate can discover it.
     * Alternative: register via spring.jpa.properties.hibernate.session_events.log=true
     * or by adding to LocalContainerEntityManagerFactoryBean properties.
     */
    @Bean
    public TenantStatementInspector tenantStatementInspector() {
        return new TenantStatementInspector();
    }
}

