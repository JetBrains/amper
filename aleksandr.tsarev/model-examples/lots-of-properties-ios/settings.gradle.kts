buildscript {
    repositories {
        maven("https://jitpack.io")
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.example:01-simple-model:1.0-SNAPSHOT")
        classpath("org.example:1-gradle-facade-binding:1.0-SNAPSHOT")
//        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.10")
    }
}

plugins.apply("org.example.settings.plugin")