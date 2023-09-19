rootProject.name = "MyApplication"

include(":androidApp")
include(":shared")
include(":desktopApp")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    plugins {
//        val kotlinVersion = extra["kotlin.version"] as String
//        val agpVersion = extra["agp.version"] as String
//        val composeVersion = extra["compose.version"] as String

        kotlin("jvm")
        kotlin("multiplatform")
        kotlin("android")

        id("com.android.application")
        id("com.android.library")

        id("org.jetbrains.compose")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
    id("org.jetbrains.deft.proto.settings.plugin").version("157-NIGHTLY")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
