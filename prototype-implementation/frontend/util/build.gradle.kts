plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    implementation(project(":frontend-api"))
    testImplementation(kotlin("test"))
}

group = "org.jetbrains.deft.proto.frontend"
version = "1.0-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["kotlin"])
        }
    }
}