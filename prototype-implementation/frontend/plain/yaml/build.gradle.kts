import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend.without-fragments.yaml"
version = "1.0-SNAPSHOT"

val junitVersion = "5.9.2"

kotlin {
    targetHierarchy.default()
    jvm {
        withJava()
    }

    sourceSets {
        val jvmMain by getting {
            languageSettings.enableLanguageFeature("ContextReceivers")
            kotlin {
                setSrcDirs(listOf("src/"))
            }
            resources.setSrcDirs(listOf("src/resources/"))
            dependencies {
                implementation(project(":frontend-api"))
                implementation(project(":frontend:util"))
                implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
                implementation("org.yaml:snakeyaml:2.0")
            }
        }

        val jvmTest by getting {
            languageSettings.enableLanguageFeature("ContextReceivers")
            kotlin {
                setSrcDirs(listOf("test/"))
            }
            resources.setSrcDirs(listOf("test/resources/"))
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
                implementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.+")
            }
        }
    }
}

tasks.withType(Test::class) {
    useJUnitPlatform()
}

tasks.withType(KotlinCompile::class).configureEach {
    javaPackagePrefix = "org.jetbrains.deft.proto.frontend"
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["kotlin"])
        }
    }
}
