package com.hospital.queue.strategy;

import com.hospital.queue.model.QueueEntry;
import org.springframework.stereotype.Component;

@Component
public class FifoStrategy implements QueuePriorityStrategy {

    @Override
    public int compare(QueueEntry a, QueueEntry b) {
        // Earlier joinedAt = higher priority (lower position)
        return a.getJoinedAt().compareTo(b.getJoinedAt());
    }

    @Override
    public String getStrategyName() {
        return "FIFO";
    }
}
