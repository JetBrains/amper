plugins {
    kotlin("jvm") version "1.7.21"
    `maven-publish`
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation("cc.ekblad:4koma:1.1.0")
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}