package com.hospital.queue.dto;

import java.util.List;

/**
 * @param waiting patients in queue order
 * @param active  patients who have been called or are with a doctor
 */
public record QueueStatusResponse(
        String departmentId,
        long version,
        String strategy,
        int waitingCount,
        int activeCount,
        List<QueueEntryResponse> waiting,
        List<QueueEntryResponse> active) {
}
