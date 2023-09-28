buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    dependencies {
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:171-NIGHTLY")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")