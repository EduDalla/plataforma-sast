package com.fiap.sast.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration lifetime;
    private final Clock clock;

    @Autowired
    public JwtService(
            JwtEncoder encoder,
            @Value("${sast.jwt.issuer}") String issuer,
            @Value("${sast.jwt.access-token-minutes}") long lifetimeMinutes) {
        this(encoder, issuer, Duration.ofMinutes(lifetimeMinutes), Clock.systemUTC());
    }

    JwtService(JwtEncoder encoder, String issuer, Duration lifetime, Clock clock) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public IssuedToken issue(String subject) {
        var issuedAt = Instant.now(clock);
        var expiresAt = issuedAt.plus(lifetime);
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", "analysis:read analysis:write")
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var parameters = JwtEncoderParameters.from(header, claims);
        return new IssuedToken(encoder.encode(parameters).getTokenValue(), lifetime.toSeconds());
    }

    public record IssuedToken(String accessToken, long expiresIn) {
    }
}
