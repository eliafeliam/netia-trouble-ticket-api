package com.netia.common.controller;

import com.netia.common.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Token generation endpoint for local development and testing ONLY.
 *
 * WHY @Profile("!prod"):
 *   This controller must NEVER be available in production. In prod, tokens are issued
 *   exclusively by Keycloak (OIDC). Exposing this endpoint in prod would allow anyone
 *   to generate arbitrary tokens for any tenantId — a critical security hole.
 *   The @Profile("!prod") annotation ensures Spring does not register this bean when
 *   SPRING_PROFILES_ACTIVE=prod.
 *
 * WHY this exists at all:
 *   Without it, testing the API locally requires running bash scripts or Python.
 *   This endpoint lets any developer get a token with a single curl command in any shell
 *   (cmd.exe, PowerShell, WSL) without any external tools.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth (dev only)", description = "Token generation — disabled in production profile")
@Profile("!prod")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public record TokenRequest(
            @NotBlank String tenantId,
            @NotBlank String userId
    ) {}

    public record TokenResponse(String token, String tenantId, String userId) {}

    @PostMapping("/token")
    @Operation(
            summary = "Generate JWT token (dev/test only)",
            description = "Returns a signed JWT for the given tenantId and userId. NOT available in prod profile."
    )
    public ResponseEntity<TokenResponse> generateToken(@RequestBody TokenRequest request) {
        String token = jwtTokenProvider.generateToken(request.tenantId(), request.userId());
        return ResponseEntity.ok(new TokenResponse(token, request.tenantId(), request.userId()));
    }
}
