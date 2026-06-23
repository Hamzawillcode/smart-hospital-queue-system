package com.hospital.queue.strategy;

import com.hospital.queue.model.QueueEntry;
import org.springframework.stereotype.Component;

@Component
public class EmergencyPriorityStrategy
        implements QueuePriorityStrategy {

    @Override
    public int compare(QueueEntry a, QueueEntry b) {
        // For emergency — also FIFO but could be extended
        // to prioritize critical patients in future
        // TODO: Add severity level to QueueEntry on Day 20
        return a.getJoinedAt().compareTo(b.getJoinedAt());
    }

    @Override
    public String getStrategyName() {
        return "EMERGENCY_PRIORITY";
    }
}
