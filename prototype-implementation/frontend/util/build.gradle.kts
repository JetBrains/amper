plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend"
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