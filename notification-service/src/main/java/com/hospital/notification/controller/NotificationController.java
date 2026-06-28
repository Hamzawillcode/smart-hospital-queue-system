package com.hospital.notification.controller;

import com.hospital.notification.dto.NotificationRequest;
import com.hospital.notification.service.NotificationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation
        .MessageMapping;
import org.springframework.messaging.handler.annotation
        .SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class NotificationController {

    private static final Logger log =
            LogManager.getLogger(NotificationController.class);

    @Autowired
    private NotificationService notificationService;

    // ─────────────────────────────────────────
    // SERVE THE PATIENT HTML PAGE
    // ─────────────────────────────────────────

    // Patient opens: localhost:8085/patient/OPD-1
    @GetMapping("/patient/{tokenNumber}")
    public String patientPage(
            @PathVariable String tokenNumber,
            Model model) {

        log.info("Patient page requested for token: {}",
                tokenNumber);

        model.addAttribute("tokenNumber", tokenNumber);
        model.addAttribute("wsUrl",
                "http://localhost:8085/ws");

        return "patient-queue";  // → templates/patient-queue.html
    }

    // ─────────────────────────────────────────
    // REST ENDPOINTS (called by other services)
    // ─────────────────────────────────────────

    // Other services call this to send position update
    @PostMapping("/api/notify/position")
    @ResponseBody
    public ResponseEntity<String> sendPositionUpdate(
            @RequestBody NotificationRequest request) {

        log.info("Position update received for token: {}",
                request.getTokenNumber());
        notificationService.sendPositionUpdate(request);
        return ResponseEntity.ok("Notification sent");
    }

    // Notify patient they are next
    @PostMapping("/api/notify/next/{tokenNumber}")
    @ResponseBody
    public ResponseEntity<String> notifyNext(
            @PathVariable String tokenNumber,
            @RequestParam String roomNumber,
            @RequestParam String doctorName,
            @RequestParam String departmentId) {

        notificationService.notifyPatientNext(
                tokenNumber, roomNumber,
                doctorName, departmentId);
        return ResponseEntity.ok("Next notification sent");
    }

    // Broadcast queue update to entire department
    @PostMapping("/api/notify/department/{deptId}")
    @ResponseBody
    public ResponseEntity<String> notifyDepartment(
            @PathVariable String deptId,
            @RequestParam int totalWaiting) {

        notificationService.notifyQueueUpdate(
                deptId, totalWaiting);
        return ResponseEntity.ok("Department notified");
    }

    // Send custom message to a patient
    @PostMapping("/api/notify/custom/{tokenNumber}")
    @ResponseBody
    public ResponseEntity<String> sendCustom(
            @PathVariable String tokenNumber,
            @RequestParam String message) {

        notificationService.sendCustomMessage(
                tokenNumber, message);
        return ResponseEntity.ok("Custom message sent");
    }

    // ─────────────────────────────────────────
    // WEBSOCKET MESSAGE MAPPING
    // Client sends to /app/ping → server responds
    // ─────────────────────────────────────────
    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public String handlePing(String message) {
        log.debug("Ping received: {}", message);
        return "pong";
    }
}
