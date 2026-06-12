package com.netia.troubleticket.mapper;

import com.netia.troubleticket.domain.Note;
import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.domain.TroubleTicketStatus;
import com.netia.troubleticket.dto.NoteResponse;
import com.netia.troubleticket.dto.TroubleTicketResponse;
import com.netia.troubleticket.dto.TroubleTicketSummary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TroubleTicketMapper {

    public TroubleTicketResponse toResponse(TroubleTicket ticket) {
        if (ticket == null) {
            return null;
        }

        List<NoteResponse> notes = ticket.getNotes() != null
                ? ticket.getNotes().stream().map(this::toNoteResponse).toList()
                : List.of();

        String status = ticket.getStatus().toString().equals("new_") ? "new" : ticket.getStatus().toString();

        return new TroubleTicketResponse(
                ticket.getId(),
                ticket.getExternalId(),
                ticket.getServiceId(),
                ticket.getDescription(),
                status,
                notes
        );
    }

    public TroubleTicketSummary toSummary(TroubleTicket ticket) {
        if (ticket == null) {
            return null;
        }

        String status = ticket.getStatus().toString().equals("new_") ? "new" : ticket.getStatus().toString();
        return new TroubleTicketSummary(
                ticket.getExternalId(),
                ticket.getServiceId(),
                ticket.getDescription(),
                status
        );
    }

    public NoteResponse toNoteResponse(Note note) {
        if (note == null) {
            return null;
        }

        return new NoteResponse(
                note.getId(),
                note.getText(),
                note.getCreatedAt()
        );
    }
}


