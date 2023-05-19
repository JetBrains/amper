plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    targetHierarchy.default()
    jvm()

    sourceSets {
        val jvmMain by getting {
            kotlin {
                setSrcDirs(listOf("src/main/kotlin"))
            }
            resources.setSrcDirs(listOf("src/main/resources"))
            dependencies {
                implementation(project(":frontend-api"))
            }
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

group = "org.jetbrains.deft.proto.frontend"
version = "1.0-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["kotlin"])
        }
    }
}