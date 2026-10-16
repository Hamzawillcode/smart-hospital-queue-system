package com.hospital.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes resolve service instances through Eureka ({@code lb://}).
 * Every route strips {@code X-Auth-*} headers so a client can never inject an
 * identity header; downstream services authenticate the forwarded JWT themselves.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("patient-service", r -> r.path("/api/patients/**", "/api/auth/**")
                        .filters(f -> f.stripPrefix(1).removeRequestHeader("X-Auth-User"))
                        .uri("lb://PATIENT-SERVICE"))
                .route("queue-service", r -> r.path("/api/queue/**")
                        .filters(f -> f.stripPrefix(1).removeRequestHeader("X-Auth-User"))
                        .uri("lb://QUEUE-SERVICE"))
                .route("doctor-service", r -> r.path("/api/doctors/**")
                        .filters(f -> f.stripPrefix(1).removeRequestHeader("X-Auth-User"))
                        .uri("lb://DOCTOR-SERVICE"))
                .route("monitor-service", r -> r.path("/api/monitor/**")
                        .filters(f -> f.stripPrefix(1).removeRequestHeader("X-Auth-User"))
                        .uri("lb://MONITOR-SERVICE"))
                // SockJS: the WebSocket upgrade needs a ws:// route, the HTTP fallback
                // transports (/ws/info, xhr_streaming, ...) need a normal http route.
                .route("notification-websocket", r -> r.order(-1).path("/ws/*/*/websocket")
                        .uri("lb:ws://NOTIFICATION-SERVICE"))
                .route("notification-sockjs", r -> r.path("/ws/**")
                        .uri("lb://NOTIFICATION-SERVICE"))
                // Patient tracking page, same origin as the WebSocket
                .route("notification-tracking", r -> r.path("/track/**")
                        .uri("lb://NOTIFICATION-SERVICE"))
                .build();
    }
}
