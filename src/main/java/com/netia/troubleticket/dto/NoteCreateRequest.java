package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoteCreateRequest(
        @NotBlank(message = "text is required")
        @Size(max = 2000, message = "text must not exceed 2000 characters")
        String text) {
}


