package com.hospital.patient.dto;

import lombok.Data;

@Data
public class CheckInRequest {
    private String name;
    private String phone;
    private String department;
}
