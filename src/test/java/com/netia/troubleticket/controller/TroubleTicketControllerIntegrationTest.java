package com.netia.troubleticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netia.common.security.JwtTokenProvider;
import com.netia.troubleticket.dto.NoteCreateRequest;
import com.netia.troubleticket.dto.TroubleTicketCloseStatusRequest;
import com.netia.troubleticket.dto.TroubleTicketCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests for the Trouble Ticket API.
 *
 * WHY @SpringBootTest (not @WebMvcTest):
 *   @WebMvcTest loads only the web layer — repositories, services, and Redis are mocked.
 *   These tests verify the complete request path including JWT auth, RLS context setup,
 *   actual DB writes via Flyway-migrated schema, and idempotency logic. Only a full
 *   context gives that confidence.
 *
 * WHY @ActiveProfiles("test"):
 *   Activates application-test.yml which points to the Testcontainers Postgres/Redis
 *   instances, uses a fixed JWT secret, and disables rate limiting for tests.
 *
 * WHY records use canonical constructors (not .builder()):
 *   Java records are implicitly final and immutable. Lombok @Builder does not apply to
 *   records — the builder would be on the class, not the record. The canonical (all-args)
 *   constructor is the idiomatic and only correct way to instantiate a record.
 *
 * Test isolation note:
 *   Tests share one Spring context (expensive to restart) but each creates tickets with
 *   unique externalIds to avoid collisions. The DB is not rolled back between tests
 *   because @SpringBootTest without @Transactional commits real transactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Trouble Ticket API Integration Tests")
class TroubleTicketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String validToken;
    private final String tenantId = "tenant-123";
    private final String userId   = "user-456";

    @BeforeEach
    void setUp() {
        validToken = jwtTokenProvider.generateToken(tenantId, userId);
    }

    // ── POST /troubleTicket ───────────────────────────────────────────────────

    @Test
    @DisplayName("Should create trouble ticket and return 201 with acknowledged status")
    void testCreateTroubleTicket() throws Exception {
        TroubleTicketCreateRequest request = new TroubleTicketCreateRequest(
                "IT-CREATE-001", 987654321L,
                "Brak transmisji danych dla usługi klienta.", "new",
                "Zgłoszenie utworzone przez konto API partnera.");

        mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.externalId").value("IT-CREATE-001"))
                .andExpect(jsonPath("$.serviceId").value(987654321L))
                // Contract: newly created ticket is returned with status "acknowledged" (SOZ simulation).
                .andExpect(jsonPath("$.status").value("acknowledged"))
                .andExpect(jsonPath("$.notes").isArray())
                .andExpect(jsonPath("$.notes[0].text").value("Zgłoszenie utworzone przez konto API partnera."));
    }

    @Test
    @DisplayName("Should return 200 on duplicate create (idempotency via externalId)")
    void testCreateTroubleTicketIdempotency() throws Exception {
        TroubleTicketCreateRequest request = new TroubleTicketCreateRequest(
                "IT-IDEM-001", 111111111L, "Test idempotency", "new", "First note");

        // First request → 201
        MvcResult first = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Second request with same externalId → 200 (idempotent, same ticket returned)
        MvcResult second = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String firstId  = extractId(first);
        String secondId = extractId(second);
        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    @DisplayName("Should return 400 when create request has status other than 'new'")
    void testCreateTroubleTicketInvalidStatus() throws Exception {
        TroubleTicketCreateRequest request = new TroubleTicketCreateRequest(
                "IT-INVALID-001", 987654321L, "Test invalid status", "closed", "Should fail");

        mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ── GET /troubleTicket ────────────────────────────────────────────────────

    @Test
    @DisplayName("Should list trouble tickets with summary fields only")
    void testListTroubleTickets() throws Exception {
        TroubleTicketCreateRequest createRequest = new TroubleTicketCreateRequest(
                "IT-LIST-001", 987654321L, "Test list", "new", "For list test");

        mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                // Contract: list returns only summary fields (externalId, serviceId, description, status).
                .andExpect(jsonPath("$[0].externalId").isNotEmpty())
                .andExpect(jsonPath("$[0].serviceId").isNumber())
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[0].status").isNotEmpty());
    }

    // ── GET /troubleTicket/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("Should return full ticket representation by id")
    void testGetTroubleTicketById() throws Exception {
        TroubleTicketCreateRequest createRequest = new TroubleTicketCreateRequest(
                "IT-GET-001", 987654321L, "Test get by id", "new", "For get test");

        MvcResult createResult = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String ticketId = extractId(createResult);

        mockMvc.perform(get("/api/v1/troubleTicket/" + ticketId)
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.externalId").value("IT-GET-001"))
                .andExpect(jsonPath("$.notes").isArray());
    }

    @Test
    @DisplayName("Should return 404 for non-existent ticket")
    void testGetNonExistentTicket() throws Exception {
        mockMvc.perform(get("/api/v1/troubleTicket/non-existent-id")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TROUBLE_TICKET_NOT_FOUND"));
    }

    // ── PATCH /troubleTicket/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("Should close trouble ticket and return status 'closed'")
    void testCloseTroubleTicket() throws Exception {
        TroubleTicketCreateRequest createRequest = new TroubleTicketCreateRequest(
                "IT-CLOSE-001", 987654321L, "Test close", "new", "For close test");

        String ticketId = extractId(mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn());

        TroubleTicketCloseStatusRequest closeRequest = new TroubleTicketCloseStatusRequest("closed");

        mockMvc.perform(patch("/api/v1/troubleTicket/" + ticketId)
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));
    }

    // ── POST /troubleTicket/{id}/note ─────────────────────────────────────────

    @Test
    @DisplayName("Should add note and return 201 with note fields")
    void testAddNoteToTroubleTicket() throws Exception {
        TroubleTicketCreateRequest createRequest = new TroubleTicketCreateRequest(
                "IT-NOTE-001", 987654321L, "Test add note", "new", "Initial note");

        String ticketId = extractId(mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn());

        NoteCreateRequest noteRequest = new NoteCreateRequest("Additional note from test");

        mockMvc.perform(post("/api/v1/troubleTicket/" + ticketId + "/note")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.text").value("Additional note from test"))
                // "date" matches the NoteResponse record field name (maps to note.createdAt).
                .andExpect(jsonPath("$.date").isNotEmpty());
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 401 when Authorization header is missing")
    void testUnauthorizedAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/troubleTicket"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
