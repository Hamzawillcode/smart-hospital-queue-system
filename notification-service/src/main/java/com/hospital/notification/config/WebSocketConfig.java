package com.hospital.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config
        .MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation
        .EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation
        .StompEndpointRegistry;
import org.springframework.web.socket.config.annotation
        .WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {

        // Prefix for messages FROM server TO client
        // Clients subscribe to /topic/xxx
        registry.enableSimpleBroker("/topic");

        // Prefix for messages FROM client TO server
        // Client sends to /app/xxx
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {

        // WebSocket connection endpoint
        // Client connects to: ws://localhost:8085/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // fallback for browsers
        // that don't support WebSocket
    }
}
