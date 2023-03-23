import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
}

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

tasks.named("compileKotlin", KotlinCompile::class) {
    applySettings()
}

tasks.named("compileTestKotlin", KotlinCompile::class) {
    applySettings()
}

tasks.test {
    useJUnitPlatform()
}

fun KotlinCompile.applySettings() {
    javaPackagePrefix = "org.jetbrains.deft.proto.frontend"
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
