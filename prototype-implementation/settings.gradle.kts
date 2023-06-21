buildscript {
    repositories {
        // Uncomment this if you use published in scratch version.

        // Uncomment this if you want to use local published version.
        //region local version
        mavenLocal()
        //region local version

        //region scratch version
        val localProperties = java.util.Properties().apply {
            val stream = rootDir.resolve("root.local.properties")
                .takeIf { it.exists() }
                ?.inputStream()
            if (stream != null) load(stream)
        }
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven {
            name = "spacePackages"
            url = java.net.URI.create("https://packages.jetbrains.team/maven/p/deft/scratch")
            credentials {
                username = localProperties.getProperty("scratch.username")
                    ?: error("No \"scratch.username\" in root.local.properties!")
                password = localProperties.getProperty("scratch.password")
                    ?: error("No \"scratch.password\" in root.local.properties!")
            }
        }
        //endregion scratch version

        mavenCentral()
        google()
        gradlePluginPortal()
    }

    dependencies {
//        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:1.0-SNAPSHOT")
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:1.2.2")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.21")
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
