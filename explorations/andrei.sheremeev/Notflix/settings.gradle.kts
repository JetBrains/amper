rootProject.name = "Notflix"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

include(":app-android")
include(":shared")
include(":app-desktop")
