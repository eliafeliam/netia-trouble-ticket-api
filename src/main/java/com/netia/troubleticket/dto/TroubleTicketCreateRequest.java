package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TroubleTicketCreateRequest(
        @NotBlank(message = "externalId is required")
        @Size(max = 100, message = "externalId must not exceed 100 characters")
        String externalId,

        @NotNull(message = "serviceId is required")
        @Min(value = 1, message = "serviceId must be greater than 0")
        Long serviceId,

        @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description must not exceed 2000 characters")
        String description,

        @NotNull(message = "status is required")
        String status,

        @NotBlank(message = "note is required")
        @Size(max = 2000, message = "note must not exceed 2000 characters")
        String note
) {
}


