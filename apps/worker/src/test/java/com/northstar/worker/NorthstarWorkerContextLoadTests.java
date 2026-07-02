package com.northstar.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gate 2 for the worker app: boots its context (scheduling + event listeners)
 * against a real Postgres and confirms every JPA entity from {@code :core}
 * validates against the schema under {@code ddl-auto: validate}. In production the
 * api owns migrations and the worker reads the already-migrated schema, so this
 * test enables Flyway (migrations travel on {@code :core}'s classpath) to bring the
 * empty Testcontainers database up to the same schema before validation runs.
 */
@SpringBootTest(properties = "spring.flyway.enabled=true")
@Testcontainers
class NorthstarWorkerContextLoadTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Test
    void contextLoads() {
        // Success = the worker context booted and schema validation passed.
    }
}
