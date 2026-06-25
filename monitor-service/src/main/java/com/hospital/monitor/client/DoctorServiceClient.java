package com.hospital.monitor.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;

@Component
public class DoctorServiceClient {

    private static final Logger log =
            LogManager.getLogger(DoctorServiceClient.class);

    private final WebClient webClient;

    public DoctorServiceClient(
            @Value("${services.doctor.url:http://localhost:8083}")
            String doctorServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(doctorServiceUrl)
                .build();
    }

    public List getAllDoctors() {
        try {
            log.debug("Calling doctor-service for all doctors");
            return webClient.get()
                    .uri("/doctors/all")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.warn("Could not reach doctor-service: {}",
                    e.getMessage());
            return List.of();
        }
    }

    public List getAvailableDoctors(String departmentId) {
        try {
            return webClient.get()
                    .uri("/doctors/department/{deptId}/available",
                            departmentId)
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.warn("Could not reach doctor-service: {}",
                    e.getMessage());
            return List.of();
        }
    }
}