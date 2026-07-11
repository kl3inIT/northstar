package com.northstar.api.auth;

import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

@NullMarked
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "northstar.auth.mobile", name = "enabled", havingValue = "true")
class MobileTokenConfiguration {

    @Bean
    SecretKey mobileJwtSecretKey(MobileAuthProperties properties) {
        return new SecretKeySpec(properties.decodedSecret(), "HmacSHA256");
    }

    @Bean
    JwtEncoder mobileJwtEncoder(SecretKey mobileJwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(mobileJwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder mobileJwtDecoder(SecretKey mobileJwtSecretKey, MobileAuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(mobileJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud", audiences -> audiences != null && audiences.contains(properties.audience()));
        OAuth2TokenValidator<Jwt> tokenUse = new JwtClaimValidator<String>(
                "token_use", "access"::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, tokenUse));
        return decoder;
    }
}
