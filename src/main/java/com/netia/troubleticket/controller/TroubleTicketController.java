package com.netia.troubleticket.controller;

import com.netia.common.security.TenantAwarePrincipal;
import com.netia.troubleticket.dto.*;
import com.netia.troubleticket.service.TroubleTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST controller for Trouble Ticket operations.
 *
 * WHY thin controller:
 *   The controller is responsible only for HTTP concerns: extracting inputs (path vars,
 *   headers, body), delegating to the service, and mapping the result to an HTTP response.
 *   All business logic lives in TroubleTicketService. This makes the controller trivially
 *   testable with MockMvc and keeps business rules easy to find and change.
 *
 * WHY @RequestMapping("/api/v1/troubleTicket"):
 *   The OpenAPI contract specifies base path /api/v{version}/troubleTicket.
 *   Versioning via path prefix is explicit, widely understood, and easy to evolve
 *   (add /api/v2/... controllers without changing existing ones).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/troubleTicket")
@RequiredArgsConstructor
@Tag(name = "TroubleTicket", description = "Operacje na zgłoszeniach Trouble Ticket")
@SecurityRequirement(name = "bearerAuth")
public class TroubleTicketController {

    private final TroubleTicketService troubleTicketService;

    /**
     * WHY the service returns a result wrapper (CreateResult) instead of checking existence twice:
     *   A naive approach would call repository.existsByExternalIdAndTenantId() before create to
     *   decide which status code to return. This adds an extra DB round-trip AND creates a
     *   TOCTOU (time-of-check / time-of-use) race condition — the ticket could be created between
     *   the check and the insert. Instead the service returns a record that includes an "isNew"
     *   flag, determined atomically during the create transaction itself.
     *
     * WHY 201 for new tickets and 200 for idempotent returns:
     *   The OpenAPI contract explicitly distinguishes these two cases. 201 indicates a new
     *   resource was created (with a Location header); 200 indicates the existing resource
     *   was returned unchanged. Clients that cache responses can treat these differently.
     *
     * WHY Location header on 201:
     *   RESTful best practice — lets the client discover the canonical URL of the new
     *   resource without parsing the body. Especially useful for API gateways and proxies.
     */
    @PostMapping
    @Operation(summary = "Utwórz zgłoszenie Trouble Ticket",
            description = "Tworzy nowe zgłoszenie przypisane do tenant scope wynikającego z Bearer tokenu.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Zgłoszenie utworzone",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TroubleTicketResponse.class))),
            @ApiResponse(responseCode = "200",
                    description = "Zwrócono istniejące zgłoszenie na podstawie idempotencji (tenantId, externalId)"),
            @ApiResponse(responseCode = "400", description = "Żądanie jest niepoprawne"),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień")
    })
    public ResponseEntity<TroubleTicketResponse> createTroubleTicket(
            @Valid @RequestBody TroubleTicketCreateRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        String tenantId = extractTenantId(authentication);
        log.info("Creating trouble ticket for tenant: {}", tenantId);

        TroubleTicketService.CreateResult result =
                troubleTicketService.createTroubleTicket(request, tenantId, idempotencyKey);

        URI location = URI.create("/api/v1/troubleTicket/" + result.ticket().id());

        if (result.isNew()) {
            return ResponseEntity.created(location).body(result.ticket());
        } else {
            // 200 OK with Location header — idempotent: returned existing ticket without side effects.
            return ResponseEntity.ok().location(location).body(result.ticket());
        }
    }

    @GetMapping
    @Operation(summary = "Listuj zgłoszenia Trouble Ticket",
            description = "Zwraca minimalną listę zgłoszeń widocznych dla uwierzytelnionego użytkownika.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista zgłoszeń",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TroubleTicketSummary.class))),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień")
    })
    public ResponseEntity<List<TroubleTicketSummary>> listTroubleTickets(Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(troubleTicketService.listTroubleTickets(tenantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pobierz pojedyncze zgłoszenie Trouble Ticket",
            description = "Zwraca pełną reprezentację zgłoszenia widocznego dla uwierzytelnionego użytkownika.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pełna reprezentacja zgłoszenia",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TroubleTicketResponse.class))),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień"),
            @ApiResponse(responseCode = "404", description = "Zgłoszenie nie znalezione")
    })
    public ResponseEntity<TroubleTicketResponse> getTroubleTicketById(
            @PathVariable String id,
            Authentication authentication) {

        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(troubleTicketService.getTroubleTicketById(id, tenantId));
    }

    /**
     * WHY PATCH instead of PUT:
     *   PATCH is semantically correct for partial updates. PUT would require the full
     *   resource representation, but clients only send the status field. PATCH signals
     *   that only specified fields are modified — aligns with the OpenAPI contract.
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Zamknij zgłoszenie Trouble Ticket",
            description = "Umożliwia publicznemu klientowi API zmianę statusu wyłącznie na 'closed'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie zostało zamknięte",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TroubleTicketResponse.class))),
            @ApiResponse(responseCode = "400", description = "Żądanie jest niepoprawne"),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień"),
            @ApiResponse(responseCode = "404", description = "Zgłoszenie nie znalezione")
    })
    public ResponseEntity<TroubleTicketResponse> closeTroubleTicket(
            @PathVariable String id,
            @Valid @RequestBody TroubleTicketCloseStatusRequest request,
            Authentication authentication) {

        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(troubleTicketService.closeTroubleTicket(id, request, tenantId));
    }

    @PostMapping("/{id}/note")
    @Operation(summary = "Dodaj notatkę do zgłoszenia Trouble Ticket",
            description = "Tworzy nową notatkę dla istniejącego zgłoszenia widocznego w tenant scope użytkownika.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notatka została dodana",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Żądanie jest niepoprawne"),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień"),
            @ApiResponse(responseCode = "404", description = "Zgłoszenie nie znalezione")
    })
    public ResponseEntity<NoteResponse> addTroubleTicketNote(
            @PathVariable String id,
            @Valid @RequestBody NoteCreateRequest request,
            Authentication authentication) {

        String tenantId = extractTenantId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(troubleTicketService.addNote(id, request, tenantId));
    }

    private String extractTenantId(Authentication authentication) {
        return ((TenantAwarePrincipal) authentication.getPrincipal()).getTenantId();
    }
}
