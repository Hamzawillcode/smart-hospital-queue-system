package com.hospital.queue.dto;

import lombok.Data;

@Data
public class AssignDoctorRequest {
    private String doctorId;
    private String doctorName;
    private String roomNumber;
}
