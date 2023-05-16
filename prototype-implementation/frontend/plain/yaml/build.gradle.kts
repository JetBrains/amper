import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend.without-fragments.yaml"
version = "1.0-SNAPSHOT"

val junitVersion = "5.9.2"
dependencies {
    implementation(project(":frontend-api"))
    implementation(project(":frontend:util"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")

    implementation("org.yaml:snakeyaml:2.0")


    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.+")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType(KotlinCompile::class).configureEach {
    javaPackagePrefix = "org.jetbrains.deft.proto.frontend"
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}
