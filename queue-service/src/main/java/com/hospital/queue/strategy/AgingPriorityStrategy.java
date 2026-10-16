package com.hospital.queue.strategy;

import com.hospital.queue.config.QueueProperties;
import com.hospital.queue.manager.QueueItem;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

/**
 * Priority with aging, so low-priority patients cannot starve:
 * effective score = priority weight + minutes waited / agingMinutesPerLevel.
 * With the default of 15, a NORMAL patient who has waited 30 minutes ranks
 * level with a CRITICAL patient who just arrived.
 */
@Component
public class AgingPriorityStrategy implements QueuePriorityStrategy {

    private final int agingMinutesPerLevel;

    public AgingPriorityStrategy(QueueProperties properties) {
        if (properties.agingMinutesPerLevel() <= 0) {
            throw new IllegalArgumentException("queue.aging-minutes-per-level must be positive");
        }
        this.agingMinutesPerLevel = properties.agingMinutesPerLevel();
    }

    @Override
    public String name() {
        return "AGING_PRIORITY";
    }

    @Override
    public Comparator<QueueItem> comparator(Instant now) {
        return Comparator
                .comparingDouble((QueueItem item) -> score(item, now)).reversed()
                .thenComparing(ARRIVAL_ORDER);
    }

    double score(QueueItem item, Instant now) {
        double waitedMinutes = Math.max(0, Duration.between(item.joinedAt(), now).toSeconds() / 60.0);
        return item.priority().weight() + waitedMinutes / agingMinutesPerLevel;
    }
}
