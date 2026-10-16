package com.hospital.monitor.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Every domain event, as published. The Kafka eventId is the document id, so a
 * redelivered event overwrites itself instead of being counted twice.
 *
 * @param outcome reason or status carried by the event (e.g. CONSULTATION_COMPLETED, BUSY)
 * @param recordedAt TTL index: events are removed after 30 days
 */
@Document(collection = "event_logs")
@CompoundIndex(name = "type_time", def = "{'eventType': 1, 'occurredAt': -1}")
public record EventLog(
        @Id String eventId,
        String eventType,
        String sourceService,
        @Indexed String departmentId,
        @Indexed String patientId,
        String outcome,
        String payload,
        Instant occurredAt,
        @Indexed(expireAfter = "30d") Instant recordedAt) {
}
