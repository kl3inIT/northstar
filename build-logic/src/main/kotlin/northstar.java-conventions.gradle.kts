// Base conventions shared by every JVM module: Java 25 toolchain, repositories,
// and JUnit Platform for tests.

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
    java
}

/**
 * org.gradle.parallel runs the modules' test tasks in separate JVMs at once;
 * concurrent Testcontainers bootstraps then race Docker Desktop's npipe and one
 * JVM caches "no Docker environment" for all its tests. This no-op shared
 * service capped at one usage makes test tasks queue behind each other while
 * compilation stays parallel.
 */
abstract class TestcontainersLock : BuildService<BuildServiceParameters.None>

val testcontainersLock = gradle.sharedServices.registerIfAbsent("testcontainersLock", TestcontainersLock::class) {
    maxParallelUsages = 1
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

// Keep reflective parameter names (Spring Boot's plugin does this for the app
// modules automatically, libraries must opt in): the assistant tools' JSON
// schemas are generated from parameter names — without this they become arg0…
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    usesService(testcontainersLock)
}
