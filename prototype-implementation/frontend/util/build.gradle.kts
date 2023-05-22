import java.net.URI

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
    repositories {
        maven {
            name = "spacePackages"
            url = URI.create("https://packages.jetbrains.team/maven/p/deft/scratch")
            credentials {
                username = rootProject.ext["spaceUsername"] as? String
                password = rootProject.ext["spacePassword"] as? String
            }
        }
    }

    publications {
        create<MavenPublication>(project.name) {
            from(components["kotlin"])
        }
    }
}