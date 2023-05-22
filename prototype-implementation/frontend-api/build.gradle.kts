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
            from(components["java"])
        }
    }
}


tasks.withType(KotlinCompile::class).configureEach {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}