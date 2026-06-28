package com.hospital.notification.dto;

import lombok.Data;

@Data
public class NotificationRequest {

    private String tokenNumber;
    private String patientName;
    private String departmentId;
    private int position;
    private int totalInQueue;
    private String status;
    private String roomNumber;
    private String doctorName;
}
