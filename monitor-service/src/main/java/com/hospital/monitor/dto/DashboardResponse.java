package com.hospital.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class DashboardResponse {

    // Total patients checked in today
    private long totalPatientsToday;

    // Total events logged today
    private long totalEventsToday;

    // Per-department queue sizes
    private Map<String, Integer> queueSizeByDepartment;

    // Available doctors per department
    private Map<String, Long> availableDoctorsByDepartment;

    // Recent 10 events
    private List<EventLogResponse> recentEvents;
}
