package com.hospital.patient.controller;

import com.hospital.patient.dto.LoginRequest;
import com.hospital.patient.dto.LoginResponse;
import com.hospital.patient.security.JwtUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication
        .AuthenticationManager;
import org.springframework.security.authentication
        .BadCredentialsException;
import org.springframework.security.authentication
        .UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log =
            LogManager.getLogger(AuthController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {

        log.info("Login attempt for username: {}",
                request.getUsername());

        try {
            Authentication auth = authenticationManager
                    .authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.getUsername(),
                                    request.getPassword()));

            String token = jwtUtil.generateToken(
                    request.getUsername());

            log.info("Login successful for user: {}",
                    request.getUsername());

            return ResponseEntity.ok(new LoginResponse(
                    token,
                    request.getUsername(),
                    "Login successful"));

        } catch (BadCredentialsException e) {
            log.warn("Login failed for username: {} — " +
                            "invalid credentials",
                    request.getUsername());

            return new ResponseEntity<>(
                    new LoginResponse(null,
                            request.getUsername(),
                            "Invalid username or password"),
                    HttpStatus.UNAUTHORIZED);
        }
    }
}
