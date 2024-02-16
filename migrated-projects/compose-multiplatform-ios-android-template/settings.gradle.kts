pluginManagement {
    repositories {
        mavenCentral()
        // add repositories:
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/amper/amper")
    }
}

plugins {
    // apply the plugin:
    id("org.jetbrains.amper.settings.plugin").version("0.3.0-dev-442")
}