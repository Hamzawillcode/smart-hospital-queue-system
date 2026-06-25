package com.hospital.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class EventLogResponse {
    private String eventType;
    private String sourceService;
    private String departmentId;
    private String patientId;
    private String payload;
    private LocalDateTime recordedAt;
}
