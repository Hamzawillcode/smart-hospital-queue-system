package com.hospital.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PatientResponse {
    private String id;
    private String name;
    private String department;
    private String status;
    private String tokenNumber;
    private LocalDateTime checkinTime;
}
