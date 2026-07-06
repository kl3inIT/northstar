plugins {
    id("northstar.spring-library-conventions")
}

dependencies {
    // Exposed to apps that depend on :core (entities, repositories, module APIs).
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.modulith:spring-modulith-starter-core")
    // ChatClient API only — the model starter (and the ChatClient bean) lives in
    // whichever app delivers the AI feature; core stays provider-agnostic.
    api("org.springframework.ai:spring-ai-client-chat")
    // Annotations-only jar: the assistant module's tools carry @McpTool metadata
    // (behavior hints) alongside @Tool, so the SAME definition serves the in-app
    // chat (ChatClient) and the mcp app (published to external agents).
    api("org.springframework.ai:spring-ai-mcp-annotations")

    // Internal to :core.
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
