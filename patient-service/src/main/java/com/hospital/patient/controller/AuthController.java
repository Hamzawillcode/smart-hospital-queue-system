package com.hospital.patient.controller;

import com.hospital.patient.dto.LoginRequest;
import com.hospital.patient.dto.LoginResponse;
import com.hospital.patient.security.JwtTokenService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LogManager.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /** Bad credentials raise AuthenticationException, mapped to a generic 401 by GlobalExceptionHandler. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        JwtTokenService.IssuedToken token = tokenService.issue(authentication);
        log.info("Issued token for {} with roles {}", authentication.getName(), token.roles());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), authentication.getName(), token.roles());
    }
}
