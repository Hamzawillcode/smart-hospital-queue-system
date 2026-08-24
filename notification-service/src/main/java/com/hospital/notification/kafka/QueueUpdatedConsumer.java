package com.hospital.notification.kafka;

import com.hospital.common.events.QueueUpdatedEvent;
import com.hospital.notification.dto.NotificationMessage;
import com.hospital.notification.dto.NotificationRequest;
import com.hospital.notification.service.NotificationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class QueueUpdatedConsumer {

    private static final Logger log =
            LogManager.getLogger(QueueUpdatedConsumer.class);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @KafkaListener(
            topics = "queue.updated",
            groupId = "notification-service-group",
            containerFactory = "queueUpdatedListenerFactory"
    )
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500, multiplier = 2),
            autoCreateTopics = "true"
    )
    public void onQueueUpdated(QueueUpdatedEvent event) {

        log.info("Received queue.updated event: dept={} " +
                        "waiting={} firstPatient={}",
                event.getDepartmentId(),
                event.getTotalWaiting(),
                event.getFirstPatientId());

        try {
            List<QueueUpdatedEvent.QueueEntrySnapshot>
                    queue = event.getQueueSnapshot();

            if (queue == null || queue.isEmpty()) {
                log.debug("Empty queue snapshot for dept: {}",
                        event.getDepartmentId());
                // Still broadcast department-level update
                broadcastDepartmentUpdate(event);
                return;
            }

            // ─────────────────────────────────────────
            // NOTIFY EACH PATIENT OF THEIR POSITION
            // This is YOUR idea from Day 1
            // ─────────────────────────────────────────
            for (QueueUpdatedEvent.QueueEntrySnapshot
                    entry : queue) {

                int position = entry.getPosition();
                int ahead = position - 1;
                int estimatedWait = entry
                        .getEstimatedWaitMinutes();

                String message = buildMessage(
                        position, ahead, estimatedWait);

                NotificationMessage notification =
                        new NotificationMessage(
                                entry.getTokenNumber(),
                                entry.getPatientName(),
                                event.getDepartmentId(),
                                position,
                                ahead,
                                estimatedWait,
                                "WAITING",
                                message,
                                null,
                                null,
                                LocalDateTime.now()
                        );

                // Push to patient's personal WebSocket topic
                String destination = "/topic/patient/"
                        + entry.getTokenNumber();

                messagingTemplate.convertAndSend(
                        destination, notification);

                log.debug("WebSocket pushed to {}: " +
                                "position={} message={}",
                        destination, position, message);

                // Special log for key positions
                if (position == 5) {
                    log.info("5-position alert pushed " +
                                    "to token: {}",
                            entry.getTokenNumber());
                }
                if (position == 1) {
                    log.info("YOU'RE NEXT alert pushed " +
                                    "to token: {}",
                            entry.getTokenNumber());
                }
            }

            // ─────────────────────────────────────────
            // BROADCAST TO ENTIRE DEPARTMENT
            // All patients in OPD see overall queue size
            // ─────────────────────────────────────────
            broadcastDepartmentUpdate(event);

            log.info("All {} patients notified in dept: {}",
                    queue.size(), event.getDepartmentId());

        } catch (Exception e) {
            log.error("Error processing queue.updated " +
                            "event for dept {}: {}",
                    event.getDepartmentId(),
                    e.getMessage());
            throw e; // Re-throw for @RetryableTopic
        }
    }

    // ─────────────────────────────────────────
    // BROADCAST QUEUE SIZE TO DEPARTMENT TOPIC
    // ─────────────────────────────────────────
    private void broadcastDepartmentUpdate(
            QueueUpdatedEvent event) {

        NotificationMessage deptNotification =
                new NotificationMessage(
                        null,
                        null,
                        event.getDepartmentId(),
                        0,
                        event.getTotalWaiting(),
                        event.getTotalWaiting() * 5,
                        "QUEUE_UPDATE",
                        "Queue updated: "
                                + event.getTotalWaiting()
                                + " patients waiting in "
                                + event.getDepartmentId(),
                        null,
                        null,
                        LocalDateTime.now()
                );

        String deptTopic = "/topic/department/"
                + event.getDepartmentId();

        messagingTemplate.convertAndSend(
                deptTopic, deptNotification);

        log.debug("Department broadcast sent to: {}",
                deptTopic);
    }

    // ─────────────────────────────────────────
    // BUILD POSITION MESSAGE
    // ─────────────────────────────────────────
    private String buildMessage(int position,
                                int ahead,
                                int estimatedWait) {
        if (position == 1) {
            return "🔔 You're next! Please be ready.";
        } else if (position == 2) {
            return "⚡ Almost your turn! 1 patient ahead.";
        } else if (position <= 5) {
            return "⏳ " + ahead
                    + " patients ahead. Please stay nearby.";
        } else {
            return "🪑 " + ahead
                    + " patients ahead. Est. wait: "
                    + estimatedWait + " mins.";
        }
    }
}
