pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/amper/amper")
    }
}

plugins {
    id("org.jetbrains.amper.settings.plugin").version("0.3.0-dev-436")
}