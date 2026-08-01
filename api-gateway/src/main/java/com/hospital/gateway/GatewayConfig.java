package com.hospital.gateway;

import org.springframework.cloud.gateway.route
        .RouteLocator;
import org.springframework.cloud.gateway.route.builder
        .RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(
            RouteLocatorBuilder builder) {

        return builder.routes()

                // ── PATIENT SERVICE ──────────────────────
                .route("patient-service", r -> r
                        .path("/api/patients/**",
                                "/api/auth/**")
                        .filters(f -> f
                                .stripPrefix(1)  // removes /api prefix
                                .addRequestHeader("X-Source", "gateway"))
                        .uri("lb://PATIENT-SERVICE"))

                // ── QUEUE SERVICE ────────────────────────
                .route("queue-service", r -> r
                        .path("/api/queue/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Source", "gateway"))
                        .uri("lb://QUEUE-SERVICE"))

                // ── DOCTOR SERVICE ───────────────────────
                .route("doctor-service", r -> r
                        .path("/api/doctors/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Source", "gateway"))
                        .uri("lb://DOCTOR-SERVICE"))

                // ── MONITOR SERVICE ──────────────────────
                .route("monitor-service", r -> r
                        .path("/api/monitor/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Source", "gateway"))
                        .uri("lb://MONITOR-SERVICE"))

                // ── NOTIFICATION SERVICE ─────────────────
                // WebSocket needs special handling
                .route("notification-ws", r -> r
                        .path("/ws/**")
                        .uri("lb:ws://NOTIFICATION-SERVICE"))

                .route("notification-api", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Source", "gateway"))
                        .uri("lb://NOTIFICATION-SERVICE"))

                .build();
    }
}
