package com.hospital.doctor.dto;

import com.hospital.doctor.model.DoctorStatus;
import jakarta.validation.constraints.NotNull;

public record DoctorStatusUpdateRequest(@NotNull DoctorStatus status) {
}
