// Base conventions shared by every JVM module: Java 25 toolchain, repositories,
// and JUnit Platform for tests.

plugins {
    java
}

group = "com.northstar"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
}
