plugins {
    kotlin("jvm") version "1.7.21"
    `maven-publish`
}

group = "org.jetbrains.deft.proto.frontend"
version = "1.0-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}


tasks.withType(KotlinCompile::class).configureEach {
    javaPackagePrefix = "org.jetbrains.deft.proto.frontend"
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}