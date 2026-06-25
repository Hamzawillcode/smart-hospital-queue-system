package com.hospital.monitor.dto;

import lombok.Data;

@Data
public class LogEventRequest {
    private String eventType;
    private String sourceService;
    private String departmentId;
    private String patientId;
    private String payload;
}
