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
    id("org.jetbrains.deft.proto.settings.plugin").version("199-NIGHTLY")
}


include(":shared")
include(":android-app")
include(":jvm-app")

