package com.hospital.queue.dto;

import com.hospital.common.events.Priority;
import com.hospital.queue.model.QueueStatus;

import java.time.Instant;

/**
 * @param position             1-based position among waiting patients; null when not waiting
 * @param estimatedWaitMinutes rough estimate from position; null when not waiting
 */
public record QueueEntryResponse(
        String queueEntryId,
        String patientId,
        String patientName,
        String departmentId,
        String tokenNumber,
        Priority priority,
        QueueStatus status,
        Integer position,
        Integer estimatedWaitMinutes,
        String doctorName,
        String roomNumber,
        Instant joinedAt) {
}
