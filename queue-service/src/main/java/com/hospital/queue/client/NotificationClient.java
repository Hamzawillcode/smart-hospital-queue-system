package com.hospital.queue.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationClient {

    private static final Logger log =
            LogManager.getLogger(NotificationClient.class);

    private final WebClient webClient;

    public NotificationClient(
            WebClient.Builder builder,
            @Value("${services.notification.url}")
            String notifyUrl) {
        this.webClient = builder
                .baseUrl(notifyUrl)
                .build();
    }

    // Send position update to a specific patient
    public void sendPositionUpdate(String tokenNumber,
                                   String patientName,
                                   String departmentId,
                                   int position,
                                   int totalInQueue) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("tokenNumber",  tokenNumber);
            body.put("patientName",  patientName);
            body.put("departmentId", departmentId);
            body.put("position",     position);
            body.put("totalInQueue", totalInQueue);
            body.put("status",       "WAITING");

            webClient.post()
                    .uri("/api/notify/position")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Position update sent: token={} pos={}",
                    tokenNumber, position);

        } catch (Exception e) {
            log.warn("Notification failed for {}: {}",
                    tokenNumber, e.getMessage());
        }
    }

    // Notify patient they are next
    public void notifyPatientNext(String tokenNumber,
                                  String roomNumber,
                                  String doctorName,
                                  String departmentId) {
        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/notify/next/{token}")
                            .queryParam("roomNumber", roomNumber)
                            .queryParam("doctorName", doctorName)
                            .queryParam("departmentId",
                                    departmentId)
                            .build(tokenNumber))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Next notification sent to: {}",
                    tokenNumber);

        } catch (Exception e) {
            log.warn("Next notification failed: {}",
                    e.getMessage());
        }
    }
}