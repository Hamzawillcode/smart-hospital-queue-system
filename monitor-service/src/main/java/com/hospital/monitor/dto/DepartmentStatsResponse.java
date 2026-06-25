package com.hospital.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DepartmentStatsResponse {
    private String departmentId;
    private int currentlyWaiting;
    private int currentlyInConsultation;
    private long availableDoctors;
    private double avgWaitMinutes;
    private long totalServedToday;
}
