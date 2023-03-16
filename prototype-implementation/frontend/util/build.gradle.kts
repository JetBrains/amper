plugins {
    kotlin("jvm") version "1.7.21"
    `maven-publish`
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":frontend-api"))
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}