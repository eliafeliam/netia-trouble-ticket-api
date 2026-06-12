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

@Slf4j
@RestController
@RequestMapping("/api/v1/troubleTicket")
@RequiredArgsConstructor
@Tag(name = "TroubleTicket", description = "Operacje na zgłoszeniach Trouble Ticket")
@SecurityRequirement(name = "bearerAuth")
public class TroubleTicketController {

    private final TroubleTicketService troubleTicketService;

    @PostMapping
    @Operation(summary = "Utwórz zgłoszenie Trouble Ticket",
            description = "Tworzy nowe zgłoszenie przypisane do tenant scope wynikającego z Bearer tokenu.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Zgłoszenie utworzone",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TroubleTicketResponse.class))),
            @ApiResponse(responseCode = "200", description = "Zwrócono istniejące zgłoszenie na podstawie idempotencji"),
            @ApiResponse(responseCode = "400", description = "Żądanie jest niepoprawne"),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień")
    })
    public ResponseEntity<TroubleTicketResponse> createTroubleTicket(
            @Valid @RequestBody TroubleTicketCreateRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        String tenantId = getTenantId(authentication);
        log.info("Creating trouble ticket for tenant: {}", tenantId);

        TroubleTicketResponse response = troubleTicketService.createTroubleTicket(request, tenantId, idempotencyKey);

        // Return 201 if newly created, 200 if idempotent
        // Since we don't track creation state, we return 201 for simplicity
        return ResponseEntity
                .created(URI.create("/api/v1/troubleTicket/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Listuj zgłoszenia Trouble Ticket",
            description = "Zwraca minimalną listę zgłoszeń widocznych dla uwierzytelnionego użytkownika.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista zgłoszeń",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TroubleTicketSummary.class))),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień")
    })
    public ResponseEntity<List<TroubleTicketSummary>> listTroubleTickets(Authentication authentication) {
        String tenantId = getTenantId(authentication);
        log.info("Listing trouble tickets for tenant: {}", tenantId);

        List<TroubleTicketSummary> tickets = troubleTicketService.listTroubleTickets(tenantId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pobierz pojedyncze zgłoszenie Trouble Ticket",
            description = "Zwraca pełną reprezentację zgłoszenia widocznego dla uwierzytelnionego użytkownika.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pełna reprezentacja zgłoszenia",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TroubleTicketResponse.class))),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień"),
            @ApiResponse(responseCode = "404", description = "Zgłoszenie nie znalezione")
    })
    public ResponseEntity<TroubleTicketResponse> getTroubleTicketById(
            @PathVariable String id,
            Authentication authentication) {

        String tenantId = getTenantId(authentication);
        log.info("Fetching trouble ticket: {} for tenant: {}", id, tenantId);

        TroubleTicketResponse response = troubleTicketService.getTroubleTicketById(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Zamknij zgłoszenie Trouble Ticket",
            description = "Umożliwia publicznemu klientowi API zmianę statusu wyłącznie na 'closed'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie zostało zamknięte",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TroubleTicketResponse.class))),
            @ApiResponse(responseCode = "400", description = "Żądanie jest niepoprawne"),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień"),
            @ApiResponse(responseCode = "404", description = "Zgłoszenie nie znalezione")
    })
    public ResponseEntity<TroubleTicketResponse> closeTroubleTicket(
            @PathVariable String id,
            @Valid @RequestBody TroubleTicketCloseStatusRequest request,
            Authentication authentication) {

        String tenantId = getTenantId(authentication);
        log.info("Closing trouble ticket: {} for tenant: {}", id, tenantId);

        TroubleTicketResponse response = troubleTicketService.closeTroubleTicket(id, request, tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/note")
    @Operation(summary = "Dodaj notatkę do zgłoszenia Trouble Ticket",
            description = "Tworzy nową notatkę dla istniejącego zgłoszenia widocznego w tenant scope użytkownika.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notatka została dodana",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Żądanie jest niepoprawne"),
            @ApiResponse(responseCode = "401", description = "Brak uwierzytelnienia"),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień"),
            @ApiResponse(responseCode = "404", description = "Zgłoszenie nie znalezione")
    })
    public ResponseEntity<NoteResponse> addTroubleTicketNote(
            @PathVariable String id,
            @Valid @RequestBody NoteCreateRequest request,
            Authentication authentication) {

        String tenantId = getTenantId(authentication);
        log.info("Adding note to trouble ticket: {} for tenant: {}", id, tenantId);

        NoteResponse response = troubleTicketService.addNote(id, request, tenantId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    private String getTenantId(Authentication authentication) {
        TenantAwarePrincipal principal = (TenantAwarePrincipal) authentication.getPrincipal();
        return principal.getTenantId();
    }
}


