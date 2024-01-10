pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("../../../../")
}

plugins {
    id("org.jetbrains.amper.settings.plugin")
}