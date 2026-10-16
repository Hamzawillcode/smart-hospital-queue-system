package com.hospital.monitor.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Current status per doctor. Only replaced by an event with a higher @Version from
 * doctor-service, so reordered or redelivered events cannot roll it back.
 */
@Document(collection = "doctor_status")
public record DoctorStatusView(
        @Id String doctorId,
        String doctorName,
        @Indexed String departmentId,
        String status,
        long version,
        Instant updatedAt) {
}
