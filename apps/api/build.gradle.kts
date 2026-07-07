plugins {
    id("northstar.spring-boot-app-conventions")
}

dependencies {
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Emits the OpenAPI contract at /v3/api-docs -> contracts/openapi.json -> web `pnpm gen:api`.
    implementation(libs.springdoc.webmvc)

    // OpenAI ChatModel autoconfig for the capture extraction (core uses ChatClient).
    implementation(libs.spring.ai.starter.openai)
    // Durable assistant conversation memory (spring_ai_chat_memory, Flyway V12).
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    // Dynamic tool discovery: only search_tools goes to the model up front; the
    // advisor expands discovered tools per conversation. Lucene backs the index
    // (lucene-core is optional in the library — the starter's pin, minus its
    // auto-config, which can't see our @Qualifier'd ChatClient).
    implementation(libs.spring.ai.tool.search.advisor)
    implementation(libs.lucene.core)
    // PgVectorStore + EmbeddingModel autoconfig — semantic half of hybrid search.
    // Schema is Flyway's (V14): spring.ai.vectorstore.pgvector.initialize-schema=false.
    implementation(libs.spring.ai.starter.pgvector)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")

    // Postgres is started manually (`docker compose up -d`) before running any app.

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
