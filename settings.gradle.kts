rootProject.name = "northstar"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Convention plugins live in an included build so every module can apply
// `northstar.*` plugins for shared configuration.
includeBuild("build-logic")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Shared domain + Spring Modulith modules (one library, used by all apps).
include(":core")

// Deployable Spring Boot applications (thin bootstraps over :core).
include(":apps:api")
include(":apps:mcp")
include(":apps:worker")
