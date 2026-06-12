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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Trouble Ticket API Integration Tests")
public class TroubleTicketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String validToken;
    private String tenantId = "tenant-123";
    private String userId = "user-456";

    @BeforeEach
    public void setUp() {
        validToken = jwtTokenProvider.generateToken(tenantId, userId);
    }

    @Test
    @DisplayName("Should create trouble ticket successfully")
    public void testCreateTroubleTicket() throws Exception {
        TroubleTicketCreateRequest request = TroubleTicketCreateRequest.builder()
                .externalId("OK-123456")
                .serviceId(987654321L)
                .description("Brak transmisji danych dla usługi klienta.")
                .status("new")
                .note("Zgłoszenie utworzone przez konto API partnera.")
                .build();

        mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.externalId").value("OK-123456"))
                .andExpect(jsonPath("$.serviceId").value(987654321L))
                .andExpect(jsonPath("$.status").value("acknowledged"))
                .andExpect(jsonPath("$.notes").isArray())
                .andExpect(jsonPath("$.notes[0].text").value("Zgłoszenie utworzone przez konto API partnera."));
    }

    @Test
    @DisplayName("Should return 200 for idempotent create")
    public void testCreateTroubleTicketIdempotency() throws Exception {
        TroubleTicketCreateRequest request = TroubleTicketCreateRequest.builder()
                .externalId("OK-999999")
                .serviceId(111111111L)
                .description("Test idempotency")
                .status("new")
                .note("First note")
                .build();

        // First request
        MvcResult firstResult = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = extractIdFromResponse(firstResult);

        // Second request with same externalId
        MvcResult secondResult = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String secondId = extractIdFromResponse(secondResult);

        // Should return same ticket
        assert firstId.equals(secondId);
    }

    @Test
    @DisplayName("Should reject create with invalid status")
    public void testCreateTroubleTicketInvalidStatus() throws Exception {
        TroubleTicketCreateRequest request = TroubleTicketCreateRequest.builder()
                .externalId("OK-111111")
                .serviceId(987654321L)
                .description("Test invalid status")
                .status("closed")
                .note("Should fail")
                .build();

        mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should list trouble tickets")
    public void testListTroubleTickets() throws Exception {
        // Create a ticket first
        TroubleTicketCreateRequest createRequest = TroubleTicketCreateRequest.builder()
                .externalId("OK-LIST-TEST")
                .serviceId(987654321L)
                .description("Test list")
                .status("new")
                .note("For list test")
                .build();

        mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // List tickets
        mockMvc.perform(get("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].externalId").isNotEmpty())
                .andExpect(jsonPath("$[0].serviceId").isNumber())
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[0].status").isNotEmpty());
    }

    @Test
    @DisplayName("Should get trouble ticket by id")
    public void testGetTroubleTicketById() throws Exception {
        // Create a ticket
        TroubleTicketCreateRequest createRequest = TroubleTicketCreateRequest.builder()
                .externalId("OK-GET-TEST")
                .serviceId(987654321L)
                .description("Test get by id")
                .status("new")
                .note("For get test")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String ticketId = extractIdFromResponse(createResult);

        // Get ticket
        mockMvc.perform(get("/api/v1/troubleTicket/" + ticketId)
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.externalId").value("OK-GET-TEST"))
                .andExpect(jsonPath("$.notes").isArray());
    }

    @Test
    @DisplayName("Should close trouble ticket")
    public void testCloseTroubleTicket() throws Exception {
        // Create a ticket
        TroubleTicketCreateRequest createRequest = TroubleTicketCreateRequest.builder()
                .externalId("OK-CLOSE-TEST")
                .serviceId(987654321L)
                .description("Test close")
                .status("new")
                .note("For close test")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String ticketId = extractIdFromResponse(createResult);

        // Close ticket
        TroubleTicketCloseStatusRequest closeRequest = TroubleTicketCloseStatusRequest.builder()
                .status("closed")
                .build();

        mockMvc.perform(patch("/api/v1/troubleTicket/" + ticketId)
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));
    }

    @Test
    @DisplayName("Should add note to trouble ticket")
    public void testAddNoteToTroubleTicket() throws Exception {
        // Create a ticket
        TroubleTicketCreateRequest createRequest = TroubleTicketCreateRequest.builder()
                .externalId("OK-NOTE-TEST")
                .serviceId(987654321L)
                .description("Test add note")
                .status("new")
                .note("Initial note")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/troubleTicket")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String ticketId = extractIdFromResponse(createResult);

        // Add note
        NoteCreateRequest noteRequest = NoteCreateRequest.builder()
                .text("Additional note from test")
                .build();

        mockMvc.perform(post("/api/v1/troubleTicket/" + ticketId + "/note")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.text").value("Additional note from test"))
                .andExpect(jsonPath("$.date").isNotEmpty());
    }

    @Test
    @DisplayName("Should return 401 without valid token")
    public void testUnauthorizedAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/troubleTicket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 404 for non-existent ticket")
    public void testGetNonExistentTicket() throws Exception {
        mockMvc.perform(get("/api/v1/troubleTicket/non-existent-id")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TROUBLE_TICKET_NOT_FOUND"));
    }

    private String extractIdFromResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return objectMapper.readTree(content).get("id").asText();
    }
}

