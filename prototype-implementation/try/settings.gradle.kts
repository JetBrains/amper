pluginManagement {
    repositories {
//        maven("https://packages.jetbrains.team/maven/p/deft/scratch")
        mavenLocal()
        mavenCentral()
        google()
    }
}

plugins {
    id("org.jetbrains.deft.proto.settings.plugin") version "1.0-SNAPSHOT"
}