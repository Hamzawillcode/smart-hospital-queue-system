package com.hospital.common.events;

import java.time.Instant;

/** Published whenever a doctor's availability changes; feeds the operations dashboard. */
public record DoctorStatusChangedEvent(
        String eventId,
        String doctorId,
        String doctorName,
        String departmentId,
        String status,
        String currentRequestId,
        long version,
        Instant occurredAt) {
}
