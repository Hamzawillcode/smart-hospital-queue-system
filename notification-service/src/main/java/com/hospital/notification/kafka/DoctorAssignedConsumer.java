package com.hospital.notification.kafka;

import com.hospital.common.events.DoctorAssignedEvent;
import com.hospital.notification.dto.NotificationMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class DoctorAssignedConsumer {

    private static final Logger log =
            LogManager.getLogger(DoctorAssignedConsumer.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @KafkaListener(
            topics = "doctor.assigned",
            groupId = "notification-service-group",
            containerFactory = "doctorAssignedListenerFactory"
    )
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500, multiplier = 2),
            autoCreateTopics = "true"
    )
    public void onDoctorAssigned(DoctorAssignedEvent event) {

        log.info("Received doctor.assigned event: " +
                        "patient={} doctor={} room={}",
                event.getPatientId(),
                event.getDoctorName(),
                event.getRoomNumber());

        try {
            // Build the "YOU'RE NEXT" notification
            // with doctor and room information
            NotificationMessage notification =
                    new NotificationMessage(
                            event.getPatientToken(),
                            null,
                            event.getDepartmentId(),
                            1,
                            0,
                            0,
                            "ASSIGNED",
                            "🔔 You're next! Please proceed" +
                                    " to " + event.getRoomNumber(),
                            event.getRoomNumber(),
                            event.getDoctorName(),
                            LocalDateTime.now()
                    );

            // Push to patient's personal WebSocket topic
            String destination = "/topic/patient/"
                    + event.getPatientToken();

            messagingTemplate.convertAndSend(
                    destination, notification);

            log.info("Doctor assignment notification " +
                            "pushed to: {} — Dr. {} Room: {}",
                    destination,
                    event.getDoctorName(),
                    event.getRoomNumber());

        } catch (Exception e) {
            log.error("Failed to send doctor.assigned " +
                    "notification: {}", e.getMessage());
            throw e;
        }
    }
}