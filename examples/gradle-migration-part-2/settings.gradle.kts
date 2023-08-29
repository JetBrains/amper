pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }

    includeBuild("../../prototype-implementation")
}

plugins {
    id("org.jetbrains.deft.proto.settings.plugin")
}