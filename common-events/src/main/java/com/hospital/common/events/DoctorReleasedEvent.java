package com.hospital.common.events;

import java.time.Instant;

/**
 * queue-service no longer needs the doctor (or the pending request) for a queue entry.
 *
 * @param doctorId null when the request was still pending (no doctor reserved yet)
 */
public record DoctorReleasedEvent(
        String eventId,
        String requestId,
        String patientId,
        String doctorId,
        String departmentId,
        ReleaseReason reason,
        Instant occurredAt) {

    public enum ReleaseReason {
        /** Consultation finished normally. */
        CONSULTATION_COMPLETED,
        /** Patient left the queue before or during the call. */
        PATIENT_LEFT,
        /** queue-service could not accept an assignment (e.g. duplicate or stale); doctor must be freed. */
        ASSIGNMENT_REJECTED
    }
}
