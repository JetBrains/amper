pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }
}

plugins {
    id("org.jetbrains.amper.settings.plugin").version("0.1.0-dev-251")
}
