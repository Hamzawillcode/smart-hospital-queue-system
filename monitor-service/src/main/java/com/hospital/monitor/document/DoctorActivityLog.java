package com.hospital.monitor.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** History of doctor availability changes (from doctor.status-changed). */
@Document(collection = "doctor_activity_logs")
public record DoctorActivityLog(
        @Id String eventId,
        @Indexed String doctorId,
        String doctorName,
        @Indexed String departmentId,
        String status,
        String requestId,
        long version,
        Instant occurredAt) {
}
