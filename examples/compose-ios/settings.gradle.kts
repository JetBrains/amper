buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }

    dependencies {
        // !!! Use syncVersions.kts to update these versions
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:151-NIGHTLY")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")