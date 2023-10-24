pluginManagement {
    repositories {
        mavenCentral()
        // add repositories:
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }
}

plugins {
    // apply the plugin:
    id("org.jetbrains.deft.proto.settings.plugin").version("215-NIGHTLY")
}

rootProject.name = "my-project-name"

include(":lib")
include(":app")


