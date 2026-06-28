package com.hospital.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationMessage {

    private String tokenNumber;
    private String patientName;
    private String departmentId;
    private int position;
    private int patientsAhead;
    private int estimatedWaitMinutes;
    private String status;
    private String message;
    private String roomNumber;
    private String doctorName;
    private LocalDateTime timestamp;
}
