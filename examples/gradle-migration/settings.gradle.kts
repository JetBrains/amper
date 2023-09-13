pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }

//  TODO Reaplce by version, when layouts are published.
    includeBuild("../../prototype-implementation")
}

plugins {
    id("org.jetbrains.deft.proto.settings.plugin")
}