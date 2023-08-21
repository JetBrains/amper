buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()

        // For dev versions of KMP Gradle plugins
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")

        // For locally published plugin versions
        mavenLocal()

        // For published version
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    dependencies {
        // !!! Use updateVersions.kts to update these versions
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:103-NIGHTLY")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.9.20-dev-6845")
        classpath("com.github.johnrengelman:shadow:8.1.1")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

include(
    "ide-plugin-231-232",
)
