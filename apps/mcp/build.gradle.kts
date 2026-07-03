plugins {
    id("northstar.spring-boot-app-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    runtimeOnly("org.postgresql:postgresql")

    // MCP server (streamable-http) — exposes the northstar_* tools to coding agents.
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // mcp does not run migrations in production (the api owns them), but the
    // context-load test boots against an empty Testcontainers Postgres, so it
    // enables Flyway (from :core's classpath migrations) to satisfy ddl-auto: validate.
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
