buildscript {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        mavenCentral()
        google()
        jcenter()
        gradlePluginPortal()
        maven {
            name = "spacePackages"
            url = java.net.URI.create("https://packages.jetbrains.team/maven/p/deft/scratch")
        }
    }

    dependencies {
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:1.1.1")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.21")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")