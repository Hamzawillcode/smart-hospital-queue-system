package com.hospital.doctor.dto;

import com.hospital.doctor.model.DoctorStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DoctorResponse {
    private String doctorId;
    private String name;
    private String departmentId;
    private String specialization;
    private String roomNumber;
    private DoctorStatus status;
}
