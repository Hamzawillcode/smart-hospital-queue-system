package com.hospital.common.events;

import java.time.Instant;

/** doctor-service atomically reserved a doctor for a request. */
public record DoctorAssignedEvent(
        String eventId,
        String requestId,
        String patientId,
        String tokenNumber,
        String doctorId,
        String doctorName,
        String roomNumber,
        String departmentId,
        Instant assignedAt) {
}
