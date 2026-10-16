package com.hospital.notification.dto;

import java.time.Instant;

/** Department-wide summary for waiting-room displays: counts only, no patient data. */
public record DepartmentQueueMessage(String departmentId, int waitingCount, int activeCount, long version,
                                     Instant timestamp) {
}
