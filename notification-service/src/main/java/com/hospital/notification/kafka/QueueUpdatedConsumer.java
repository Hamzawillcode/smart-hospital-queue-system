package com.hospital.notification.kafka;

import com.hospital.common.events.KafkaTopics;
import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * No retry topics here on purpose: queue.updated is a full snapshot, so if pushing
 * one fails the next snapshot supersedes it. A short in-place retry (see
 * KafkaConsumerConfig) then log-and-skip is enough, and keeps per-department order.
 */
@Component
public class QueueUpdatedConsumer {

    private final NotificationService notificationService;

    public QueueUpdatedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = KafkaTopics.QUEUE_UPDATED)
    public void onQueueUpdated(QueueUpdatedEvent event) {
        notificationService.onQueueUpdated(event);
    }
}
