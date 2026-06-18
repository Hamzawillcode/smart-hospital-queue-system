package com.hospital.patient.controller;

import com.hospital.patient.dto.LoginRequest;
import com.hospital.patient.dto.LoginResponse;
import com.hospital.patient.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {
        try {
            // 1. Verify username + password
            Authentication auth = authenticationManager
                    .authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.getUsername(),
                                    request.getPassword()));

            // 2. If valid, generate JWT token
            String token = jwtUtil.generateToken(
                    request.getUsername());

            // 3. Return token to client
            return ResponseEntity.ok(new LoginResponse(
                    token,
                    request.getUsername(),
                    "Login successful"
            ));

        } catch (BadCredentialsException e) {
            return new ResponseEntity<>(
                    new LoginResponse(
                            null,
                            request.getUsername(),
                            "Invalid username or password"),
                    HttpStatus.UNAUTHORIZED);
        }
    }
}
