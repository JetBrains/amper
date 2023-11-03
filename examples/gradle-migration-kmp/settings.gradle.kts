pluginManagement {
    repositories {
        mavenCentral()
        // add repositories:
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/amper/amper")
    }
}

plugins {
    // apply the plugin:
    id("org.jetbrains.amper.settings.plugin").version("0.1.0-dev-246")
}


include(":shared")
include(":android-app")
include(":jvm-app")

