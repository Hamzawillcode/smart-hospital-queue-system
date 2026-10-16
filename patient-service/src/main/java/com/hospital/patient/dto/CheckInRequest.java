package com.hospital.patient.dto;

import com.hospital.common.events.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param priority triage priority; NORMAL when omitted */
public record CheckInRequest(
        @NotBlank(message = "Name cannot be empty") @Size(max = 255) String name,
        @NotBlank(message = "Phone cannot be empty")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits") String phone,
        @NotBlank(message = "Department cannot be empty") @Size(max = 32) String department,
        Priority priority) {
}
