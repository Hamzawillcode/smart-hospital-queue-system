package com.hospital.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DoctorRegisterRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,31}$", message = "must be an upper-case department code")
        String departmentId,
        @NotBlank @Size(max = 255) String specialization,
        @NotBlank @Size(max = 32) String roomNumber) {
}
