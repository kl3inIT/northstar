plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// Plugin versions are hardcoded here to match gradle/libs.versions.toml.
// (Version catalogs are not directly accessible from precompiled convention
// plugins; keep these in sync with the catalog.)
dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.0")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}
