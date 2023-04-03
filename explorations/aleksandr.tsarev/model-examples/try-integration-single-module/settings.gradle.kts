buildscript {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        mavenLocal()
        mavenCentral()
        google()
        jcenter()
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.example:01-simple-model:1.0-SNAPSHOT")
        classpath("org.example:1-gradle-facade-binding:1.0-SNAPSHOT")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.10")
    }
}

plugins.apply("org.example.settings.plugin")