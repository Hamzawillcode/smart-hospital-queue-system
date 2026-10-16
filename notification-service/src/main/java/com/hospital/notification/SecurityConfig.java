package com.hospital.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Patients have no accounts, so the tracking page and WebSocket are public; access
 * is controlled by the unguessable access code in the URL and topic. Everything
 * else on this service is denied. CSRF is off because the only state-changing
 * requests are SockJS transport frames, and clients cannot send STOMP messages.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // SockJS iframe transport needs same-origin framing
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/track/**", "/error").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
