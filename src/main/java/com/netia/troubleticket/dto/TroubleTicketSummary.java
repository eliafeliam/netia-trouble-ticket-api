package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TroubleTicketSummary(String externalId, Long serviceId, String description, String status) {
}


