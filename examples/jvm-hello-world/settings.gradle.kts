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
        classpath("org.jetbrains.deft.proto.frontend:frontend-api:1.0-SNAPSHOT")
        // Uncomment this to use without fragments.
        classpath("org.jetbrains.deft.proto.frontend.without-fragments.yaml:yaml:1.0-SNAPSHOT")
        // Uncomment this to use with fragments.
//        classpath("org.jetbrains.deft.proto.frontend.with-fragments.yaml:yaml:1.0-SNAPSHOT")
        classpath("org.jetbrains.deft.proto.settings.plugin:gradle-integration:1.0-SNAPSHOT")
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.10")
    }
}

plugins.apply("org.jetbrains.deft.proto.settings.plugin")