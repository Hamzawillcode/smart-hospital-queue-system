package com.hospital.notification.controller;

import com.hospital.notification.dto.NotificationMessage;
import com.hospital.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Controller
public class NotificationController {

    private static final Pattern ACCESS_CODE = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** The page linked from the check-in response: /track/{accessCode}. */
    @GetMapping("/track/{accessCode}")
    public String trackingPage(@PathVariable String accessCode, Model model) {
        if (!ACCESS_CODE.matcher(accessCode).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("accessCode", accessCode);
        return "patient-queue";
    }

    /**
     * Answered directly to the subscribing client (not via the broker): the current
     * state the moment the page connects or reconnects.
     */
    @SubscribeMapping("/patient/{accessCode}")
    public NotificationMessage currentState(@DestinationVariable String accessCode) {
        return notificationService.currentView(accessCode).orElseGet(NotificationMessage::unknown);
    }
}
