package com.northstar.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gate 2: boots the full api context against a real Postgres (same pgvector image
 * as compose.yaml). This is what proves — beyond a clean compile — that Flyway V1
 * applies cleanly and that every JPA entity matches the migrated schema under
 * {@code ddl-auto: validate}. If an entity column drifts from the migration, this
 * test fails at startup, not a user.
 */
@SpringBootTest
@Testcontainers
class NorthstarApiContextLoadTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Test
    void contextLoads() {
        // Success = the context booted: Flyway ran and schema validation passed.
    }
}
