package com.hospital.doctor.dto;

import com.hospital.doctor.model.Doctor;
import com.hospital.doctor.model.DoctorStatus;

public record DoctorResponse(
        String doctorId,
        String name,
        String departmentId,
        String specialization,
        String roomNumber,
        DoctorStatus status,
        String currentRequestId) {

    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(doctor.getDoctorId(), doctor.getName(), doctor.getDepartmentId(),
                doctor.getSpecialization(), doctor.getRoomNumber(), doctor.getStatus(), doctor.getCurrentRequestId());
    }
}
