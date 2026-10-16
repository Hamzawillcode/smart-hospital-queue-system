package com.hospital.patient.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Staff accounts. Passwords come from environment variables (see .env.example);
 * they are BCrypt-hashed at startup and never logged.
 *
 * <pre>
 * app.security.users.reception.password=${STAFF_PASSWORD}
 * app.security.users.reception.roles=STAFF
 * </pre>
 */
@ConfigurationProperties(prefix = "app.security")
public record StaffUsersProperties(Map<String, StaffUser> users) {

    public record StaffUser(String password, List<String> roles) {
    }
}
