package com.netia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * WHY @SpringBootApplication:
 *   Combines @Configuration + @EnableAutoConfiguration + @ComponentScan.
 *   Auto-configuration wires up JPA, Security, Redis, Actuator, and Flyway based on
 *   classpath dependencies and application.yml properties — no XML, no manual bean wiring.
 *
 * WHY virtual threads are configured in application.yml (not here):
 *   spring.threads.virtual.enabled=true is the Spring Boot 3.2+ idiomatic way to enable
 *   Project Loom virtual threads. It automatically configures the Tomcat executor,
 *   @Async thread pool, and scheduled tasks without any code changes here.
 */
@SpringBootApplication
public class TroubleTicketApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TroubleTicketApiApplication.class, args);
    }
}

