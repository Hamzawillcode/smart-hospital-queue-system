package com.hospital.doctor.dto;

import com.hospital.doctor.model.DoctorStatus;
import lombok.Data;

@Data
public class DoctorStatusUpdateRequest {
    private DoctorStatus status;
}
