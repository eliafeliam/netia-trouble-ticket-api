package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TroubleTicketCloseStatusRequest(@NotNull(message = "status is required") String status) {
}


