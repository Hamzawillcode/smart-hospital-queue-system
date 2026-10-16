package com.hospital.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No downstream services run here, so an authenticated request ends in 503
 * (no instance) rather than 200; what matters is whether the gateway let it through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.jwt.secret=" + ApiGatewayApplicationTests.SECRET,
        "eureka.client.enabled=false"
})
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {

    static final String SECRET = "integration-test-secret-key-0123456789abcdef";

    @Autowired WebTestClient client;

    @Test
    void protectedRoutesRequireAValidToken() {
        client.get().uri("/api/queue/OPD/status").exchange().expectStatus().isUnauthorized();

        client.get().uri("/api/queue/OPD/status")
                .header("Authorization", "Bearer " + token(SECRET, Instant.now().plusSeconds(600)))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void expiredOrForeignTokensAreRejected() {
        client.get().uri("/api/doctors/all")
                .header("Authorization", "Bearer " + token(SECRET, Instant.now().minusSeconds(600)))
                .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/doctors/all")
                .header("Authorization", "Bearer " + token("another-secret-key-that-is-long-enough!!", Instant.now().plusSeconds(600)))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void loginTrackingAndWebSocketPathsArePublic() {
        client.post().uri("/api/auth/login").exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
        client.get().uri("/track/abcdefghijklmnopqrstuv").exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
        client.get().uri("/ws/info").exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    private static String token(String secret, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("hospital-auth").subject("reception")
                .issuedAt(expiresAt.minusSeconds(1200)).expiresAt(expiresAt)
                .claim("roles", List.of("STAFF")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
