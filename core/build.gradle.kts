plugins {
    id("northstar.spring-library-conventions")
}

dependencies {
    // Exposed to apps that depend on :core (entities, repositories, module APIs).
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.modulith:spring-modulith-starter-core")

    // Internal to :core.
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
