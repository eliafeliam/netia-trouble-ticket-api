package com.netia.troubleticket.domain;

public enum TroubleTicketStatus {
    new_,
    acknowledged,
    inProgress,
    resolved,
    closed,
    rejected;

    @Override
    public String toString() {
        return this.name().equals("new_") ? "new" : this.name();
    }

    public static TroubleTicketStatus fromString(String value) {
        if ("new".equals(value)) {
            return new_;
        }
        return TroubleTicketStatus.valueOf(value);
    }
}

