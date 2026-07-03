// Conventions for a Spring library module (e.g. :core) that must NOT produce an
// executable boot jar. It imports the Boot + Modulith BOMs so Spring
// dependencies can be declared without versions, but does not apply the Spring
// Boot plugin.

plugins {
    `java-library`
    id("northstar.java-conventions")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.0")
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
    }
}
