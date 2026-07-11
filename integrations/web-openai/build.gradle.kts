plugins {
    id("northstar.spring-library-conventions")
}

dependencies {
    api(project(":core"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-web")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
