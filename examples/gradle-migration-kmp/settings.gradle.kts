buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()

        // add repositories:
        google()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    dependencies {
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:185-NIGHTLY")
    }
}
// apply the plugin:
plugins.apply("org.jetbrains.deft.proto.settings.plugin")


include(":shared")
include(":android-app")
include(":jvm-app")

