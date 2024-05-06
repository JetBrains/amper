plugins {
    java
    kotlin("jvm") 
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Use an Amper Module as a dependency
    implementation(project(":lib"))
    testImplementation(kotlin("test"))
    implementation(libs.gson)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}