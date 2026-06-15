package com.netia.troubleticket.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for TroubleTicketStatus.
 *
 * WHY this converter is needed:
 *   @Enumerated(EnumType.STRING) stores enum.name() — which would be "NEW", "IN_PROGRESS" etc.
 *   But our OpenAPI contract (and the DB CHECK constraint) uses lowercase camelCase values:
 *   "new", "acknowledged", "inProgress", "closed", etc.
 *
 *   This converter stores/reads status.getValue() (e.g. "inProgress") instead of the enum
 *   constant name, keeping DB values consistent with the API contract. It also means the DB
 *   column values are human-readable and match what operators see in logs and API responses.
 *
 * WHY autoApply = true:
 *   Automatically applies to every @Column of type TroubleTicketStatus across all entities,
 *   so we never forget to annotate a field manually.
 */
@Converter(autoApply = true)
public class TroubleTicketStatusConverter implements AttributeConverter<TroubleTicketStatus, String> {

    @Override
    public String convertToDatabaseColumn(TroubleTicketStatus status) {
        return status == null ? null : status.getValue();
    }

    @Override
    public TroubleTicketStatus convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : TroubleTicketStatus.fromValue(dbValue);
    }
}
