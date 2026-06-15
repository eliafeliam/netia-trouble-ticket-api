package com.netia.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight custom health endpoint at /health.
 *
 * WHY this exists alongside /actuator/health:
 *   /actuator/health (Spring Boot Actuator) is the authoritative health check used by
 *   Kubernetes liveness and readiness probes — it checks DB and Redis connections.
 *   This /health endpoint is a simpler "is the JVM up?" signal for human operators,
 *   monitoring dashboards, and smoke tests that don't need the full actuator payload.
 *
 * WHY not delegate to HealthEndpoint bean:
 *   HealthEndpoint aggregates all health indicators (DB, Redis, disk space) and can be
 *   slow under load if dependencies are degraded. This endpoint returns a static response
 *   instantly — it tells you the application started successfully, nothing more.
 *   For deep dependency health, use /actuator/health/readiness.
 */
@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Application liveness — use /actuator/health/readiness for full dependency check")
public class HealthController {

    @GetMapping
    @Operation(summary = "Simple liveness check", description = "Returns UP if the application is running.")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "trouble-ticket-api");
        body.put("version", "1.0.0");
        return ResponseEntity.ok(body);
    }
}
