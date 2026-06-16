# ─── Build stage ─────────────────────────────────────────────────────────────
# WHY maven:eclipse-temurin-21: official Maven image pinned to the same JDK version
# as the app so there are no toolchain surprises between CI and local builds.
FROM maven:3.9.7-eclipse-temurin-21 AS builder

WORKDIR /app

# Disable SSL verification — required when corporate proxy/firewall intercepts HTTPS.
ENV MAVEN_OPTS="-Djavax.net.ssl.trustAll=true -Dmaven.resolver.transport=wagon -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true"

# Copy the POM first so Docker layer-caching re-downloads dependencies ONLY when
# pom.xml changes, not on every source code edit — dramatically faster rebuilds.
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -q -Dmaven.resolver.transport=wagon -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true

# ─── Runtime stage ────────────────────────────────────────────────────────────
# WHY eclipse-temurin:21-jre-alpine:
#   * JRE (not JDK) — no compiler, smaller attack surface.
#   * Alpine — minimal OS, fewer CVEs compared to Debian/Ubuntu base images.
#   * eclipse-temurin is the de-facto production OpenJDK distribution (Eclipse Foundation).
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# WHY non-root user: running as root inside a container is a security misconfiguration.
# If the process is compromised, a non-root user limits what an attacker can do on the host.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/trouble-ticket-api-*.jar app.jar
RUN chown appuser:appgroup app.jar

EXPOSE 8080
USER appuser

# WHY this JVM flag ordering:
#   * JVM flags (-XX:...) MUST come BEFORE -jar, otherwise the JVM interprets them as
#     application arguments (a common Dockerfile mistake).
#   * G1GC: default GC for Java 9+, but explicit declaration makes it visible in review.
#   * UseStringDeduplication: reduces heap for apps with many duplicate strings (log messages, tenant IDs).
#   * ExitOnOutOfMemoryError: crash fast instead of limping — Kubernetes will restart the pod,
#     which is safer than running in a degraded OOM state.
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]


