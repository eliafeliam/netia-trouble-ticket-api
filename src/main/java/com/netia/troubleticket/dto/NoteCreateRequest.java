package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoteCreateRequest(@NotBlank(message = "text is required") String text) {
}


