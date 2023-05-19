plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend.with-fragments.yaml"
version = "1.0-SNAPSHOT"

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
                implementation(project(":frontend:util"))
                implementation("org.yaml:snakeyaml:2.0")
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

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["kotlin"])
        }
    }
}