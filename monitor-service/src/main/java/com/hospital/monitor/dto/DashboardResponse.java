package com.hospital.monitor.dto;

import java.time.Instant;
import java.util.List;

public record DashboardResponse(
        Instant generatedAt,
        long checkInsToday,
        long consultationsCompletedToday,
        long openDeadLetters,
        List<DepartmentStatsResponse> departments,
        List<EventLogResponse> recentEvents) {
}
