package com.hospital.monitor.client;

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
            @Value("${services.queue.url:http://localhost:8082}")
            String queueServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(queueServiceUrl)
                .build();
    }

    public Map getQueueStatus(String departmentId) {
        try {
            log.debug("Calling queue-service for dept: {}",
                    departmentId);
            return webClient.get()
                    .uri("/queue/{deptId}/status",
                            departmentId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("Could not reach queue-service: {}",
                    e.getMessage());
            return Map.of("totalWaiting", 0,
                    "totalAssigned", 0);
        }
    }

    public Map getAllQueues() {
        try {
            return webClient.get()
                    .uri("/queue/all")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("Could not reach queue-service: {}",
                    e.getMessage());
            return Map.of();
        }
    }
}
