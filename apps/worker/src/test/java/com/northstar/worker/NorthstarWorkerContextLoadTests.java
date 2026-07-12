package com.northstar.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
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
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=2000"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class NorthstarWorkerContextLoadTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Test
    void contextLoads() {
        // Success = the worker context booted and schema validation passed.
    }
}
