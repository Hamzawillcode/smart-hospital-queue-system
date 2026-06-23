package com.hospital.queue.strategy;

import com.hospital.queue.model.QueueEntry;

public interface QueuePriorityStrategy {
    int compare(QueueEntry a, QueueEntry b);
    String getStrategyName();
}
