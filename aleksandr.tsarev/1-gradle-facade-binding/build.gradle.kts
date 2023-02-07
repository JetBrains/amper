plugins {
    kotlin("jvm") version "1.7.21"
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":0-core-model"))
    implementation("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.8.10")
}

gradlePlugin {
    plugins {
        create("exampleSettingsPlugin") {
            id = "org.example.settings.plugin"
            implementationClass = "org.example.BindingSettingsPlugin"
        }
    }
}