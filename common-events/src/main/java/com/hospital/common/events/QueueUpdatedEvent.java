package com.hospital.common.events;

import java.time.Instant;
import java.util.List;

/**
 * Full, versioned snapshot of one department's queue, published by queue-service
 * after every committed change.
 *
 * Consumers keep the highest version they have seen per department and drop
 * anything older, which makes the event safe to redeliver or receive out of order.
 */
public record QueueUpdatedEvent(
        String eventId,
        String departmentId,
        long version,
        String strategy,
        List<WaitingEntry> waiting,
        List<ActiveEntry> active,
        Instant occurredAt) {

    /** A patient still waiting, in queue order. Position 1 is next to be called. */
    public record WaitingEntry(
            String queueEntryId,
            String patientId,
            String patientName,
            String tokenNumber,
            String accessCode,
            Priority priority,
            int position,
            int estimatedWaitMinutes,
            Instant joinedAt) {
    }

    /** A patient who has been called (AWAITING_DOCTOR) or is with a doctor (IN_CONSULTATION). */
    public record ActiveEntry(
            String queueEntryId,
            String patientId,
            String patientName,
            String tokenNumber,
            String accessCode,
            String status,
            String doctorName,
            String roomNumber,
            Instant calledAt) {
    }
}
