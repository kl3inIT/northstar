// Conventions for a deployable Spring Boot application (:apps:api, :apps:mcp,
// :apps:worker). Applies the Boot + dependency-management plugins and imports
// the Modulith BOM. Each app declares its own starters and depends on :core.

plugins {
    id("northstar.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.0")
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
    }
}
