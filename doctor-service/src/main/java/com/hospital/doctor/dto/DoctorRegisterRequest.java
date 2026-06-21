package com.hospital.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DoctorRegisterRequest {

    @NotBlank(message = "Name cannot be empty")
    private String name;

    @NotBlank(message = "Department cannot be empty")
    private String departmentId;

    @NotBlank(message = "Specialization cannot be empty")
    private String specialization;

    @NotBlank(message = "Room number cannot be empty")
    private String roomNumber;
}
