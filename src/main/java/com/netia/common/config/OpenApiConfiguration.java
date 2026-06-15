package com.netia.common.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc / Swagger UI configuration.
 *
 * WHY SpringDoc (not springfox):
 *   Springfox is abandoned (last release 2020) and incompatible with Spring Boot 3.x.
 *   SpringDoc (springdoc-openapi) is the actively maintained successor with native
 *   Spring Boot 3 / Spring Security 6 support and OpenAPI 3.1 compliance.
 *
 * WHY @SecurityScheme annotation here (not in application.yml):
 *   The @SecurityScheme annotation registers the "bearerAuth" security scheme in the
 *   OpenAPI spec, which makes the "Authorize" button appear in Swagger UI. Without it,
 *   the padlock icons on endpoints would not link to any defined scheme.
 *
 * WHY server URL /api/v1:
 *   The OpenAPI contract defines the base path as /api/v{version}. Setting it here
 *   ensures "Try it out" requests in Swagger UI are sent to the correct path.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT Bearer token. Generate one via /auth/token or generate-token.sh. " +
                      "In production this is replaced by a Keycloak-issued OIDC token."
)
public class OpenApiConfiguration {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trouble Ticket API")
                        .version("1.0.0")
                        .description("Minimalny, design-first profil TMF621 Trouble Ticket dla Netia SA")
                        .contact(new Contact()
                                .name("Netia SA")
                                .url("https://www.netia.pl"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("/api/v1")
                                .description("v1 — current public API base path")
                ));
    }
}
