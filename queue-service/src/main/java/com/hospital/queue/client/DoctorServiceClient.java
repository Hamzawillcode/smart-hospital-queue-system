package com.hospital.queue.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component
public class DoctorServiceClient {

    private static final Logger log =
            LogManager.getLogger(DoctorServiceClient.class);

    private final WebClient webClient;

    public DoctorServiceClient(
            WebClient.Builder builder,
            @Value("${services.doctor.url}")
            String doctorUrl) {
        this.webClient = builder
                .baseUrl(doctorUrl)
                .build();
    }

    // Find and immediately assign first available doctor
    public Map findAndAssignDoctor(String departmentId) {
        try {
            log.info("Finding available doctor in: {}",
                    departmentId);

            return webClient.get()
                    .uri("/doctors/department/{deptId}" +
                            "/first-available", departmentId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

        } catch (Exception e) {
            log.warn("No doctor available in: {} — {}",
                    departmentId, e.getMessage());
            return null;
        }
    }

    public void freeDoctor(String doctorId) {
        try {
            webClient.put()
                    .uri("/doctors/{id}/free", doctorId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("Doctor: {} freed", doctorId);

        } catch (Exception e) {
            log.warn("Failed to free doctor: {}",
                    e.getMessage());
        }
    }
}
