package com.hospital.patient.dto;

import com.hospital.patient.model.PatientStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PatientResponse {
    private String id;
    private String name;
    private String department;
    private PatientStatus status;
    private String tokenNumber;
    private LocalDateTime checkinTime;
}
