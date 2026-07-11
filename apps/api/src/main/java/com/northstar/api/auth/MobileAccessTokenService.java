package com.northstar.api.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@NullMarked
@Service
@ConditionalOnProperty(prefix = "northstar.auth.mobile", name = "enabled", havingValue = "true")
class MobileAccessTokenService {

    private final JwtEncoder encoder;

    private final MobileAuthProperties properties;

    private final Clock clock = Clock.systemUTC();

    MobileAccessTokenService(JwtEncoder encoder, MobileAuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    AccessToken issue(String username) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(username)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("token_use", "access")
                .claim("roles", List.of("USER"))
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }

    record AccessToken(String value, Instant expiresAt) {
    }
}
