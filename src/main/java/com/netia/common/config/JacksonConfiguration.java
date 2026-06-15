package com.netia.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson ObjectMapper configuration.
 *
 * WHY JavaTimeModule:
 *   Java 8+ date/time types (LocalDateTime, Instant, etc.) are not serialisable by
 *   Jackson out of the box. JavaTimeModule adds the required serialisers/deserialisers
 *   so that LocalDateTime fields in DTOs are converted to/from ISO-8601 strings.
 *
 * WHY WRITE_DATES_AS_TIMESTAMPS = false:
 *   Without this, Jackson serialises LocalDateTime as a numeric array [2024,6,14,12,0,0]
 *   which is opaque and non-standard. Disabling timestamps forces ISO-8601 string output
 *   (e.g. "2024-06-14T12:00:00") which matches the OpenAPI contract and is universally
 *   parseable by API clients.
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
