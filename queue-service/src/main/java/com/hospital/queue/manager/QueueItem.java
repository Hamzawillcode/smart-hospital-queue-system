package com.hospital.queue.manager;

import com.hospital.common.events.Priority;

import java.time.Instant;
import java.time.LocalDate;

/** Immutable in-memory view of a waiting patient. Never a JPA entity, so it is safe to share across threads. */
public record QueueItem(
        String queueEntryId,
        String patientId,
        String patientName,
        String tokenNumber,
        int tokenSequence,
        LocalDate checkinDate,
        Priority priority,
        String accessCode,
        Instant joinedAt) {
}
