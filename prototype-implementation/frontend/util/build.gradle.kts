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

val spaceUsername: String? by rootProject.extra
val spacePassword: String? by rootProject.extra

publishing {
    repositories {
        if (spaceUsername != null && spacePassword != null)
            maven {
                name = "spacePackages"
                url = URI.create("https://packages.jetbrains.team/maven/p/deft/scratch")
                credentials {
                    username = spaceUsername
                    password = spacePassword
                }
            }
    }

    publications {
        create<MavenPublication>(project.name) {
            from(components["kotlin"])
        }
    }
}