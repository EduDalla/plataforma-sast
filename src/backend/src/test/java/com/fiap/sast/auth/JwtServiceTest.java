package com.fiap.sast.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceTest {
    @Test
    void emitsTokenWithTwoHourLifetime() {
        var secret = new SecretKeySpec(new byte[32], "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
        var decoder = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var service = new JwtService(encoder, "sast-api", Duration.ofHours(2), Clock.fixed(now, ZoneOffset.UTC));

        var issued = service.issue("user@example.com");

        assertEquals(7200, issued.expiresIn());
        var decoded = decoder.decode(issued.accessToken());
        assertEquals(now, decoded.getIssuedAt());
        assertEquals(now.plus(Duration.ofHours(2)), decoded.getExpiresAt());
        assertEquals("user@example.com", decoded.getSubject());
    }
}
