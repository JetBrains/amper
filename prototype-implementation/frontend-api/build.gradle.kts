import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            kotlin {
                setSrcDirs(listOf("src/main/kotlin"))
            }
            resources.setSrcDirs(listOf("src/main/resources"))
        }
        val jvmTest by getting {
            kotlin {
                setSrcDirs(listOf("src/test/kotlin"))
            }
            resources.setSrcDirs(listOf("src/test/resources"))
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["kotlin"])
        }
    }
}


tasks.withType(KotlinCompile::class).configureEach {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}