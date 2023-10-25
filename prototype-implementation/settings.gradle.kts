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

        // Create local.properties.
        rootDir.resolve("local.properties").also {
            if (!it.exists()) {
                it.writeText("scratch.username=\nscratch.password=")
            }
        }
    }

    dependencies {
        // !!! Use syncVersions.sh to update these versions
        classpath("org.jetbrains.amper.settings.plugin:gradle-integration:218-NIGHTLY")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.9.20-RC")
        classpath("com.github.johnrengelman:shadow:8.1.1")
    }
}

plugins.apply("org.jetbrains.amper.settings.plugin")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.4.0")
}
