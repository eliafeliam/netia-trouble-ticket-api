package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TroubleTicketResponse(
        String id,
        String externalId,
        Long serviceId,
        String description,
        String status,
        List<NoteResponse> notes
) {
}


