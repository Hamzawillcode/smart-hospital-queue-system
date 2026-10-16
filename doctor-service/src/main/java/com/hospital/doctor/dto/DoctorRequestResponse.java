package com.hospital.doctor.dto;

import com.hospital.common.events.Priority;
import com.hospital.doctor.model.DoctorRequest;

import java.time.Instant;

public record DoctorRequestResponse(
        String requestId,
        String patientId,
        String tokenNumber,
        String departmentId,
        Priority priority,
        DoctorRequest.Status status,
        String doctorId,
        Instant requestedAt,
        Instant assignedAt) {

    public static DoctorRequestResponse from(DoctorRequest r) {
        return new DoctorRequestResponse(r.getRequestId(), r.getPatientId(), r.getTokenNumber(), r.getDepartmentId(),
                r.getPriority(), r.getStatus(), r.getDoctorId(), r.getRequestedAt(), r.getAssignedAt());
    }
}
