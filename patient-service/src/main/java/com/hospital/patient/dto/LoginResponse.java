package com.hospital.patient.dto;

import java.time.Instant;
import java.util.List;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt,
                            String username, List<String> roles) {
}
