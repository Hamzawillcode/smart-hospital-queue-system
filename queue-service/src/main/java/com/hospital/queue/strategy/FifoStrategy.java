package com.hospital.queue.strategy;

import com.hospital.queue.manager.QueueItem;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;

/** First come, first served. */
@Component
public class FifoStrategy implements QueuePriorityStrategy {

    @Override
    public String name() {
        return "FIFO";
    }

    @Override
    public Comparator<QueueItem> comparator(Instant now) {
        return ARRIVAL_ORDER;
    }
}
