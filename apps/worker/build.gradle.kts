plugins {
    id("northstar.spring-boot-app-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":integrations:web-openai"))
    implementation(project(":integrations:ai-openai-compatible"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // The worker owns search indexing (SearchIndexingWorker): OpenAI for the
    // vision captions + embeddings, PgVectorStore for the vectors it writes.
    // This is the heavy LLM/Tika work kept off the api's request threads.
    implementation(libs.spring.ai.starter.openai)
    implementation(libs.spring.ai.starter.pgvector)
    implementation(libs.db.scheduler.boot4)

    runtimeOnly("org.postgresql:postgresql")

    // Added later:
    // - ShedLock, to stop @Scheduled jobs running twice across instances

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // worker does not run migrations in production (the api owns them), but the
    // context-load test boots against an empty Testcontainers Postgres, so it
    // enables Flyway (from :core's classpath migrations) to satisfy ddl-auto: validate.
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
