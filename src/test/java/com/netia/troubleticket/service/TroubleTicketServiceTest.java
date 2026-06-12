package com.netia.troubleticket.service;

import com.netia.common.exception.ResourceNotFoundException;
import com.netia.common.exception.ValidationException;
import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.dto.TroubleTicketCreateRequest;
import com.netia.troubleticket.dto.TroubleTicketResponse;
import com.netia.troubleticket.mapper.TroubleTicketMapper;
import com.netia.troubleticket.repository.TroubleTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Trouble Ticket Service Unit Tests")
public class TroubleTicketServiceTest {

    @Mock
    private TroubleTicketRepository repository;

    @Mock
    private TroubleTicketMapper mapper;

    @InjectMocks
    private TroubleTicketService service;

    @Test
    @DisplayName("Should validate status on create - reject invalid status")
    public void testCreateWithInvalidStatus() {
        TroubleTicketCreateRequest request = TroubleTicketCreateRequest.builder()
                .externalId("TEST-123")
                .serviceId(123L)
                .description("Test")
                .status("closed")
                .note("Test note")
                .build();

        assertThatThrownBy(() -> service.createTroubleTicket(request, "tenant-1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("niedozwoloną wartością");
    }

    @Test
    @DisplayName("Should reject invalid status on close")
    public void testCloseWithInvalidStatus() {
        TroubleTicket ticket = TroubleTicket.builder()
                .id("TT-123")
                .tenantId("tenant-1")
                .externalId("EXT-123")
                .serviceId(123L)
                .description("Test")
                .status(TroubleTicketStatus.new_)
                .build();

        when(repository.findByIdAndTenantId("TT-123", "tenant-1"))
                .thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.closeTroubleTicket("TT-123",
                new com.netia.troubleticket.dto.TroubleTicketCloseStatusRequest("inProgress"), "tenant-1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for non-existent ticket")
    public void testGetNonExistentTicket() {
        when(repository.findByIdAndTenantId("non-existent", "tenant-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTroubleTicketById("non-existent", "tenant-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TROUBLE_TICKET_NOT_FOUND");
    }

    @Test
    @DisplayName("Should enforce tenant isolation")
    public void testTenantIsolation() {
        when(repository.findByIdAndTenantId("TT-123", "tenant-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTroubleTicketById("TT-123", "tenant-2"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository).findByIdAndTenantId("TT-123", "tenant-2");
    }
}

