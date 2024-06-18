pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("../../../../..") // <REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>
}

plugins {
    id("org.jetbrains.amper.settings.plugin")
}

include(":common-args")
include(":platform-args")
