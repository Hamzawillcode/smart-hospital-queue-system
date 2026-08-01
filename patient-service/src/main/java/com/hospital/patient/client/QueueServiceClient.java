package com.hospital.patient.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component
public class QueueServiceClient {

    private static final Logger log =
            LogManager.getLogger(QueueServiceClient.class);

    private final WebClient webClient;

    public QueueServiceClient(
            WebClient.Builder builder,
            @Value("${services.queue.url}")
            String queueUrl) {
        this.webClient = builder
                .baseUrl(queueUrl)
                .build();
    }

    public void addToQueue(String patientId,
                           String patientName,
                           String departmentId,
                           String tokenNumber) {
        try {
            log.info("Calling queue-service for patient: {}",
                    patientId);

            Map<String, String> body = Map.of(
                    "patientId",    patientId,
                    "patientName",  patientName,
                    "departmentId", departmentId,
                    "tokenNumber",  tokenNumber
            );

            webClient.post()
                    .uri("/queue/add")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("Patient: {} added to queue-service",
                    patientId);

        } catch (Exception e) {
            // Don't fail patient checkin if queue-service
            // is temporarily unavailable
            log.error("Failed to add to queue: {}",
                    e.getMessage());
        }
    }
}
