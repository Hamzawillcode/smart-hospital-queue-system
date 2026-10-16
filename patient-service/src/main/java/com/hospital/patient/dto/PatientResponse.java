package com.hospital.patient.dto;

import com.hospital.common.events.Priority;
import com.hospital.patient.model.Patient;
import com.hospital.patient.model.PatientStatus;

import java.time.Instant;

/**
 * @param trackingPath page the patient opens (through the gateway) to follow their position live
 */
public record PatientResponse(
        String id,
        String name,
        String department,
        Priority priority,
        PatientStatus status,
        String tokenNumber,
        Instant checkinTime,
        String assignedDoctorName,
        String roomNumber,
        String accessCode,
        String trackingPath) {

    public static PatientResponse from(Patient p) {
        return new PatientResponse(p.getId(), p.getName(), p.getDepartment(), p.getPriority(), p.getStatus(),
                p.getTokenNumber(), p.getCheckinTime(), p.getAssignedDoctorName(), p.getRoomNumber(),
                p.getAccessCode(), "/track/" + p.getAccessCode());
    }
}
