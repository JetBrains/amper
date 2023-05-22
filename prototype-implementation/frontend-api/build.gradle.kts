import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend"
version = "1.0-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))
}

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
            from(components["java"])
        }
    }
}


tasks.withType(KotlinCompile::class).configureEach {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}