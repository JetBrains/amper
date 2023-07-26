buildscript {
    repositories {
        // Uncomment this if you use published in scratch version.

        // Uncomment this if you want to use local published version.
        //region local version
        mavenLocal()
        //region local version

        //region scratch version
        java.util.Properties().apply {
            rootDir.resolve("root.local.properties")
                .also {
                    if (!it.exists()) {
                        it.writeText("scratch.username=\nscratch.password=")
                    }
                }
                .inputStream()
                .let { load(it) }
        }
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven {
            name = "spacePackages"
            url = java.net.URI.create("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
        }
        // TODO remove after internal preview
        maven {
            name = "legacySpacePackages"
            url = java.net.URI.create("https://packages.jetbrains.team/maven/p/deft/scratch")
        }
        //endregion scratch version

        mavenCentral()
        google()
        gradlePluginPortal()
    }

    dependencies {
        // !!! Use updateVersions.kts to update these versions
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:78-STABLE")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.9.20-dev-6845")
        classpath("com.github.johnrengelman:shadow:8.1.1")
        classpath("org.jetbrains.compose:compose-gradle-plugin:1.4.1")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

include(
    "ide-plugin",
)

rootProject.name = "prototype-implementation"
