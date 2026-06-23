package com.hospital.queue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddToQueueRequest {

    @NotBlank(message = "Patient ID cannot be empty")
    private String patientId;

    @NotBlank(message = "Patient name cannot be empty")
    private String patientName;

    @NotBlank(message = "Department cannot be empty")
    private String departmentId;

    @NotBlank(message = "Token number cannot be empty")
    private String tokenNumber;
}
