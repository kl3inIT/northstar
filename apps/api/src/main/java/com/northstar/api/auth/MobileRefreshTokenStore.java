package com.northstar.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@NullMarked
@Service
@ConditionalOnProperty(prefix = "northstar.auth.mobile", name = "enabled", havingValue = "true")
class MobileRefreshTokenStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;

    private final MobileAuthProperties properties;

    private final Clock clock = Clock.systemUTC();

    MobileRefreshTokenStore(JdbcClient jdbc, MobileAuthProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional
    IssuedRefreshToken issue(String username) {
        return insert(username, UUID.randomUUID(), clock.instant());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    RotatedRefreshToken rotate(String presentedToken) {
        Instant now = clock.instant();
        StoredRefreshToken current = jdbc.sql("""
                        SELECT id, family_id, username, expires_at, used_at, revoked_at
                        FROM mobile_refresh_token
                        WHERE token_hash = :token_hash
                        FOR UPDATE
                        """)
                .param("token_hash", hash(presentedToken))
                .query(MobileRefreshTokenStore::mapToken)
                .optional()
                .orElseThrow(MobileRefreshTokenStore::invalidToken);

        if (current.usedAt() != null || current.revokedAt() != null) {
            revokeFamily(current.familyId(), now);
            throw invalidToken();
        }
        if (!current.expiresAt().isAfter(now)) {
            revokeFamily(current.familyId(), now);
            throw invalidToken();
        }

        int updated = jdbc.sql("""
                        UPDATE mobile_refresh_token
                        SET used_at = :used_at
                        WHERE id = :id AND used_at IS NULL AND revoked_at IS NULL
                        """)
                .param("used_at", Timestamp.from(now))
                .param("id", current.id())
                .update();
        if (updated != 1) {
            revokeFamily(current.familyId(), now);
            throw invalidToken();
        }

        IssuedRefreshToken replacement = insert(current.username(), current.familyId(), now);
        return new RotatedRefreshToken(current.username(), replacement);
    }

    @Transactional
    void revoke(String presentedToken) {
        Instant now = clock.instant();
        jdbc.sql("""
                        UPDATE mobile_refresh_token
                        SET revoked_at = COALESCE(revoked_at, :revoked_at)
                        WHERE family_id = (
                            SELECT family_id FROM mobile_refresh_token WHERE token_hash = :token_hash
                        )
                        """)
                .param("revoked_at", Timestamp.from(now))
                .param("token_hash", hash(presentedToken))
                .update();
    }

    private IssuedRefreshToken insert(String username, UUID familyId, Instant now) {
        String value = randomToken();
        Instant expiresAt = now.plus(properties.refreshTtl());
        jdbc.sql("""
                        INSERT INTO mobile_refresh_token
                            (id, family_id, token_hash, username, expires_at, created_at)
                        VALUES
                            (:id, :family_id, :token_hash, :username, :expires_at, :created_at)
                        """)
                .param("id", UUID.randomUUID())
                .param("family_id", familyId)
                .param("token_hash", hash(value))
                .param("username", username)
                .param("expires_at", Timestamp.from(expiresAt))
                .param("created_at", Timestamp.from(now))
                .update();
        return new IssuedRefreshToken(value, expiresAt);
    }

    private void revokeFamily(UUID familyId, Instant now) {
        jdbc.sql("""
                        UPDATE mobile_refresh_token
                        SET revoked_at = COALESCE(revoked_at, :revoked_at)
                        WHERE family_id = :family_id
                        """)
                .param("revoked_at", Timestamp.from(now))
                .param("family_id", familyId)
                .update();
    }

    private static StoredRefreshToken mapToken(ResultSet result, int rowNumber) throws SQLException {
        var usedAt = result.getTimestamp("used_at");
        var revokedAt = result.getTimestamp("revoked_at");
        return new StoredRefreshToken(
                result.getObject("id", UUID.class),
                result.getObject("family_id", UUID.class),
                result.getString("username"),
                result.getTimestamp("expires_at").toInstant(),
                usedAt == null ? null : usedAt.toInstant(),
                revokedAt == null ? null : revokedAt.toInstant());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }

    record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    record RotatedRefreshToken(String username, IssuedRefreshToken refreshToken) {
    }

    private record StoredRefreshToken(
            UUID id,
            UUID familyId,
            String username,
            Instant expiresAt,
            @Nullable Instant usedAt,
            @Nullable Instant revokedAt) {
    }
}
