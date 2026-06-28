package com.hospital.notification.service.impl;

import com.hospital.notification.dto.NotificationMessage;
import com.hospital.notification.dto.NotificationRequest;
import com.hospital.notification.service.NotificationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class NotificationServiceImpl
        implements NotificationService {

    private static final Logger log =
            LogManager.getLogger(NotificationServiceImpl.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ─────────────────────────────────────────
    // SEND POSITION UPDATE
    // ─────────────────────────────────────────
    @Override
    public void sendPositionUpdate(
            NotificationRequest request) {

        log.info("Sending position update to token: {} " +
                        "position: {}",
                request.getTokenNumber(),
                request.getPosition());

        int patientsAhead = request.getPosition() - 1;
        int estimatedWait = patientsAhead * 5;

        // Build the message
        String message = buildPositionMessage(
                request.getPosition(), patientsAhead);

        NotificationMessage notification =
                new NotificationMessage(
                        request.getTokenNumber(),
                        request.getPatientName(),
                        request.getDepartmentId(),
                        request.getPosition(),
                        patientsAhead,
                        estimatedWait,
                        request.getStatus(),
                        message,
                        request.getRoomNumber(),
                        request.getDoctorName(),
                        LocalDateTime.now()
                );

        // Send to patient's specific topic
        // Patient browser subscribed to this topic receives it
        String destination = "/topic/patient/"
                + request.getTokenNumber();

        messagingTemplate.convertAndSend(
                destination, notification);

        log.info("Notification sent to: {}", destination);

        // Special notifications at key positions
        if (request.getPosition() == 5) {
            log.info("5-position alert sent to token: {}",
                    request.getTokenNumber());
        }

        if (request.getPosition() == 1) {
            log.info("Next-in-line alert sent to token: {}",
                    request.getTokenNumber());
        }
    }

    // ─────────────────────────────────────────
    // NOTIFY PATIENT THEY ARE NEXT
    // ─────────────────────────────────────────
    @Override
    public void notifyPatientNext(String tokenNumber,
                                  String roomNumber,
                                  String doctorName,
                                  String departmentId) {

        log.info("Notifying patient next: {} room: {}",
                tokenNumber, roomNumber);

        NotificationMessage notification =
                new NotificationMessage(
                        tokenNumber,
                        null,
                        departmentId,
                        1,
                        0,
                        0,
                        "ASSIGNED",
                        "🔔 You're next! Please proceed to "
                                + roomNumber,
                        roomNumber,
                        doctorName,
                        LocalDateTime.now()
                );

        messagingTemplate.convertAndSend(
                "/topic/patient/" + tokenNumber,
                notification);

        log.info("Next notification sent to: {}", tokenNumber);
    }

    // ─────────────────────────────────────────
    // NOTIFY ALL PATIENTS IN DEPARTMENT
    // ─────────────────────────────────────────
    @Override
    public void notifyQueueUpdate(String departmentId,
                                  int totalWaiting) {

        log.info("Broadcasting queue update for dept: {} " +
                        "total: {}",
                departmentId, totalWaiting);

        // Broadcast to department-wide topic
        // All patients in this dept receive this
        NotificationMessage notification =
                new NotificationMessage(
                        null,
                        null,
                        departmentId,
                        0,
                        totalWaiting,
                        totalWaiting * 5,
                        "QUEUE_UPDATE",
                        "Queue updated: " + totalWaiting
                                + " patients waiting",
                        null,
                        null,
                        LocalDateTime.now()
                );

        messagingTemplate.convertAndSend(
                "/topic/department/" + departmentId,
                notification);
    }

    // ─────────────────────────────────────────
    // SEND CUSTOM MESSAGE
    // ─────────────────────────────────────────
    @Override
    public void sendCustomMessage(String tokenNumber,
                                  String message) {

        log.info("Sending custom message to: {}",
                tokenNumber);

        NotificationMessage notification =
                new NotificationMessage(
                        tokenNumber,
                        null,
                        null,
                        0,
                        0,
                        0,
                        "CUSTOM",
                        message,
                        null,
                        null,
                        LocalDateTime.now()
                );

        messagingTemplate.convertAndSend(
                "/topic/patient/" + tokenNumber,
                notification);
    }

    // ─────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────
    private String buildPositionMessage(int position,
                                        int ahead) {
        if (position == 1) {
            return "🔔 You're next! Please be ready.";
        } else if (position == 2) {
            return "⚡ Almost your turn! 1 patient ahead.";
        } else if (position <= 5) {
            return "⏳ " + ahead + " patients ahead of you. "
                    + "Please stay nearby.";
        } else {
            return "🪑 " + ahead + " patients ahead. "
                    + "Estimated wait: " + (ahead * 5)
                    + " minutes.";
        }
    }
}
