package com.hospital.monitor.dto;

import java.time.Instant;
import java.util.Map;

/**
 * @param doctorsByStatus e.g. {AVAILABLE=1, BUSY=2}
 * @param avgWaitMinutes  average wait of currently waiting patients at the last snapshot
 */
public record DepartmentStatsResponse(
        String departmentId,
        String strategy,
        long snapshotVersion,
        int waiting,
        int active,
        double avgWaitMinutes,
        double longestWaitMinutes,
        Map<String, Long> doctorsByStatus,
        long checkInsToday,
        long completedToday,
        Instant lastUpdated) {
}
