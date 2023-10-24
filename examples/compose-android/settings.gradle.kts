pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }
}

plugins {
    id("org.jetbrains.deft.proto.settings.plugin").version("215-NIGHTLY")
}
