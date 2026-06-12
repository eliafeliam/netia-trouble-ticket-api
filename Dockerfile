FROM maven:3.9.7-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# Download dependencies
RUN mvn dependency:resolve

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built jar from builder
COPY --from=builder /app/target/trouble-ticket-api-*.jar app.jar

# Create non-root user and adjust permissions
RUN addgroup -S appgroup && adduser -S appuser -G appgroup || true
RUN chown -R appuser:appgroup /app || true

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "app.jar"]


