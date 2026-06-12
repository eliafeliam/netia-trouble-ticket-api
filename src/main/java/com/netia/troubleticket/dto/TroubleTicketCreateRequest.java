package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TroubleTicketCreateRequest(
        @NotBlank(message = "externalId is required") String externalId,
        @NotNull(message = "serviceId is required") @Min(value = 1, message = "serviceId must be greater than 0") Long serviceId,
        @NotBlank(message = "description is required") String description,
        @NotNull(message = "status is required") String status,
        @NotBlank(message = "note is required") String note
) {
}


