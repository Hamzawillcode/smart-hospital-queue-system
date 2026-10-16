package com.hospital.common.events;

import java.time.Instant;

/**
 * queue-service asks doctor-service for a doctor for the patient at the head of a queue.
 *
 * @param requestId the queue entry id; doctor-service uses it as the idempotency key
 */
public record DoctorRequestedEvent(
        String eventId,
        String requestId,
        String patientId,
        String tokenNumber,
        String departmentId,
        Priority priority,
        Instant requestedAt) {
}
