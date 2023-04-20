plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

include(
    "frontend-api",
    "frontend:util",
    "gradle-integration",
    "frontend:fragments:yaml",
    "frontend:plain:yaml",
    "ide-plugin"
)

rootProject.name = "prototype-implementation"
