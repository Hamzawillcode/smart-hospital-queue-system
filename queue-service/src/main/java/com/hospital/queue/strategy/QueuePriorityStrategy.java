package com.hospital.queue.strategy;

import com.hospital.queue.manager.QueueItem;

import java.time.Instant;
import java.util.Comparator;

/**
 * Strategy pattern: how a department orders its waiting patients.
 *
 * Add a new ordering by implementing this interface as a Spring bean and mapping
 * departments to its {@link #name()} with {@code queue.strategy.departments.<DEPT>=<NAME>}.
 * No other code changes are needed.
 */
public interface QueuePriorityStrategy {

    String name();

    /**
     * @param now fixed for the whole sort, so time-dependent strategies still give a
     *            consistent total order
     */
    Comparator<QueueItem> comparator(Instant now);

    /** Arrival order: check-in date, then the token sequence allocated under patient-service's row lock. */
    Comparator<QueueItem> ARRIVAL_ORDER = Comparator
            .comparing(QueueItem::checkinDate)
            .thenComparingInt(QueueItem::tokenSequence)
            .thenComparing(QueueItem::queueEntryId);
}
