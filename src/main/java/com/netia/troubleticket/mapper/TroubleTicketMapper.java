package com.netia.troubleticket.mapper;

import com.netia.troubleticket.domain.Note;
import com.netia.troubleticket.domain.TroubleTicket;
import com.netia.troubleticket.dto.NoteResponse;
import com.netia.troubleticket.dto.TroubleTicketResponse;
import com.netia.troubleticket.dto.TroubleTicketSummary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual mapper from domain entities to API DTOs.
 *
 * WHY manual mapper instead of MapStruct:
 *   The mapping is trivial — flat field copies + status.getValue(). MapStruct adds
 *   compile-time annotation processing overhead with no meaningful gain for this scope.
 *
 * WHY status.getValue() (not status.name()):
 *   getValue() returns the OpenAPI contract string ("inProgress", "closed").
 *   name() returns the Java enum constant name ("IN_PROGRESS", "CLOSED") which would
 *   break client parsers expecting the contract values.
 *
 * WHY stream().map().toList() in toResponse():
 *   Entity.getNotes() is always initialised to an empty ArrayList (see TroubleTicket
 *   entity). The null-guard is unnecessary — removing it eliminates dead code and
 *   makes the intent clearer: "map every note to its response DTO."
 */
@Component
public class TroubleTicketMapper {

    public TroubleTicketResponse toResponse(TroubleTicket ticket) {
        if (ticket == null) return null;

        // WHY stream().map().toList(): concise, immutable result, no null-guard needed
        // because the notes list is always initialised in the entity builder.
        List<NoteResponse> notes = ticket.getNotes().stream()
                .map(this::toNoteResponse)
                .toList();

        return new TroubleTicketResponse(
                ticket.getId(),
                ticket.getExternalId(),
                ticket.getServiceId(),
                ticket.getDescription(),
                ticket.getStatus().getValue(),
                notes
        );
    }

    public TroubleTicketSummary toSummary(TroubleTicket ticket) {
        if (ticket == null) return null;

        return new TroubleTicketSummary(
                ticket.getExternalId(),
                ticket.getServiceId(),
                ticket.getDescription(),
                ticket.getStatus().getValue()
        );
    }

    public NoteResponse toNoteResponse(Note note) {
        if (note == null) return null;

        return new NoteResponse(
                note.getId(),
                note.getText(),
                note.getCreatedAt()
        );
    }
}
