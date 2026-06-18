package com.hospital.patient.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    // ─────────────────────────────────────────
    // DEFINE WHO CAN ACCESS WHAT
    // ─────────────────────────────────────────
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http
                // Disable CSRF (not needed for REST APIs)
                .csrf(csrf -> csrf.disable())

                // Define access rules
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoint — completely public
                        .requestMatchers("/auth/**").permitAll()

                        // Patient checking own status — public
                        .requestMatchers(HttpMethod.GET,
                                "/patients/{id}").permitAll()

                        // H2 console — public (dev only)
                        .requestMatchers("/h2-console/**").permitAll()

                        // Everything else requires JWT
                        .anyRequest().authenticated()
                )

                // Stateless — no sessions (JWT handles state)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                // Add our JWT filter BEFORE Spring's default filter
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─────────────────────────────────────────
    // HARDCODED USERS (fine for dev)
    // ─────────────────────────────────────────
    @Bean
    public UserDetailsService userDetailsService() {

        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder()
                        .encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails receptionist = User.builder()
                .username("receptionist")
                .password(passwordEncoder()
                        .encode("recep123"))
                .roles("STAFF")
                .build();

        return new InMemoryUserDetailsManager(
                admin, receptionist);
    }

    // ─────────────────────────────────────────
    // PASSWORD ENCODER
    // ─────────────────────────────────────────
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ─────────────────────────────────────────
    // AUTHENTICATION MANAGER
    // ─────────────────────────────────────────
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
