package com.northstar.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@NullMarked
@RestController
@RequestMapping("/api/auth/mobile")
@ConditionalOnProperty(prefix = "northstar.auth.mobile", name = "enabled", havingValue = "true")
class MobileAuthController {

    private final AuthenticationManager authenticationManager;

    private final MobileAccessTokenService accessTokens;

    private final MobileRefreshTokenStore refreshTokens;

    MobileAuthController(AuthenticationManager authenticationManager, MobileAccessTokenService accessTokens,
            MobileRefreshTokenStore refreshTokens) {
        this.authenticationManager = authenticationManager;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/login")
    @Operation(operationId = "loginMobile")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        }
        catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password", exception);
        }
        return issue(authenticated.getName(), refreshTokens.issue(authenticated.getName()));
    }

    @PostMapping("/refresh")
    @Operation(operationId = "refreshMobileToken")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        MobileRefreshTokenStore.RotatedRefreshToken rotated = refreshTokens.rotate(request.refreshToken());
        return issue(rotated.username(), rotated.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "logoutMobile")
    void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokens.revoke(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(operationId = "getMobileAuthSession")
    MobileSession me(Authentication authentication) {
        return new MobileSession(authentication.getName());
    }

    private TokenResponse issue(String username, MobileRefreshTokenStore.IssuedRefreshToken refreshToken) {
        MobileAccessTokenService.AccessToken accessToken = accessTokens.issue(username);
        return new TokenResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.expiresAt(),
                username);
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }

    record TokenResponse(
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            String username) {
    }

    record MobileSession(String username) {
    }
}
