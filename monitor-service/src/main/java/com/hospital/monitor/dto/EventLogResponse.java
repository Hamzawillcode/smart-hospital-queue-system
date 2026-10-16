package com.hospital.monitor.dto;

import com.hospital.monitor.document.EventLog;

import java.time.Instant;

/** Event metadata without the raw payload (which can contain patient names). */
public record EventLogResponse(
        String eventId,
        String eventType,
        String sourceService,
        String departmentId,
        String patientId,
        String outcome,
        Instant occurredAt) {

    public static EventLogResponse from(EventLog e) {
        return new EventLogResponse(e.eventId(), e.eventType(), e.sourceService(), e.departmentId(), e.patientId(),
                e.outcome(), e.occurredAt());
    }
}
