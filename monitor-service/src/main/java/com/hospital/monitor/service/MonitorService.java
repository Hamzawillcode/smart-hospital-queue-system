package com.hospital.monitor.service;

import com.hospital.monitor.dto.*;
import java.util.List;

public interface MonitorService {

    // Log any event from any service
    void logEvent(LogEventRequest request);

    // Hospital-wide dashboard
    DashboardResponse getDashboard();

    // Stats for one department
    DepartmentStatsResponse getDepartmentStats(
            String departmentId);

    // Recent events
    List<EventLogResponse> getRecentEvents(int limit);

    // Events for a specific patient
    List<EventLogResponse> getPatientHistory(
            String patientId);

    // Take a snapshot of current queue state
    void takeQueueSnapshot(String departmentId);
}
