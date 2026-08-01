package com.hospital.patient.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component
public class MonitorClient {

    private static final Logger log =
            LogManager.getLogger(MonitorClient.class);

    private final WebClient webClient;

    public MonitorClient(
            WebClient.Builder builder,
            @Value("${services.monitor.url}")
            String monitorUrl) {
        this.webClient = builder
                .baseUrl(monitorUrl)
                .build();
    }

    public void logEvent(String eventType,
                         String patientId,
                         String departmentId,
                         String payload) {
        try {
            Map<String, String> body = Map.of(
                    "eventType",     eventType,
                    "sourceService", "patient-service",
                    "patientId",     patientId,
                    "departmentId",  departmentId,
                    "payload",       payload
            );

            webClient.post()
                    .uri("/monitor/log")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("Event logged to monitor: {}",
                    eventType);

        } catch (Exception e) {
            // Monitor logging failure should never
            // break the main flow
            log.warn("Monitor log failed: {}",
                    e.getMessage());
        }
    }
}
