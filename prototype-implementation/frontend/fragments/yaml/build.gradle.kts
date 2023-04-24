plugins {
    kotlin("jvm") version "1.7.21"
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend.with-fragments.yaml"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":frontend-api"))
    implementation(project(":frontend:util"))
    implementation("org.yaml:snakeyaml:2.0")
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}