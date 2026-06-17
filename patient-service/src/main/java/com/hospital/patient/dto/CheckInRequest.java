package com.hospital.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CheckInRequest {

    @NotBlank(message = "Name cannot be empty")
    private String name;

    @NotBlank(message = "Phone cannot be empty")
    @Pattern(regexp = "^[0-9]{10}$",
            message = "Phone must be exactly 10 digits")
    private String phone;

    @NotBlank(message = "Department cannot be empty")
    private String department;
}
