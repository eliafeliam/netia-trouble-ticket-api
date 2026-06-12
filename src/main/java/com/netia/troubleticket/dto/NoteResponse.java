package com.netia.troubleticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoteResponse(
        String id,
        String text,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'") LocalDateTime date
) {
}


