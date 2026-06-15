package com.hospital.patient.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Patient {
    private String id;
    private String name;
    private String phone;
    private String department;
    private String status;
    private LocalDateTime checkinTime;
}
