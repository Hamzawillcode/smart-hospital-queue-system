package com.hospital.common.events;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Published by patient-service (through its outbox) after a check-in commits.
 *
 * @param tokenSequence per-department, per-day sequence allocated under a row lock;
 *                      it defines the exact arrival order, so consumers never rely
 *                      on Kafka delivery order (retries may reorder messages)
 * @param accessCode    unguessable code the patient uses to follow their position
 */
public record PatientCheckedInEvent(
        String eventId,
        String patientId,
        String patientName,
        String departmentId,
        String tokenNumber,
        int tokenSequence,
        LocalDate checkinDate,
        Priority priority,
        String accessCode,
        Instant checkedInAt) {
}
