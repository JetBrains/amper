pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("../../../../")
}

plugins {
    id("org.jetbrains.deft.proto.settings.plugin")
}