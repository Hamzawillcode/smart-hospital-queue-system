package com.hospital.queue.strategy;

import com.hospital.queue.manager.QueueItem;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;

/** Strict triage: CRITICAL before URGENT before NORMAL; arrival order within the same priority. */
@Component
public class EmergencyPriorityStrategy implements QueuePriorityStrategy {

    private static final Comparator<QueueItem> ORDER = Comparator
            .comparingInt((QueueItem item) -> item.priority().weight()).reversed()
            .thenComparing(ARRIVAL_ORDER);

    @Override
    public String name() {
        return "PRIORITY";
    }

    @Override
    public Comparator<QueueItem> comparator(Instant now) {
        return ORDER;
    }
}
