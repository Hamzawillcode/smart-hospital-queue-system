package com.hospital.notification.dto;

import java.time.Instant;

/**
 * What a patient's screen shows. Deliberately contains no patient name: the topic is
 * protected only by the unguessable access code, so the payload carries no PII.
 *
 * @param status  WAITING, CALLED, IN_CONSULTATION, COMPLETED or UNKNOWN
 * @param version queue snapshot version; the page ignores anything older than what it shows
 */
public record NotificationMessage(
        String tokenNumber,
        String departmentId,
        String status,
        Integer position,
        Integer patientsAhead,
        Integer estimatedWaitMinutes,
        String message,
        String doctorName,
        String roomNumber,
        long version,
        Instant timestamp) {

    public static NotificationMessage unknown() {
        return new NotificationMessage(null, null, "UNKNOWN", null, null, null,
                "Waiting for the first queue update…", null, null, -1, Instant.now());
    }
}
