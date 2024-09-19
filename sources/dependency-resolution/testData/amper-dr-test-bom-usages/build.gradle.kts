plugins {
    kotlin("jvm") version "2.0.20"
    `maven-publish`
}

group = "org.jetbrains.amper"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    // Dependency on a Ktor BOM
    implementation(platform("io.ktor:ktor-bom:2.3.9"))
    // Dependency without version (version is resolved from BOM)
    implementation("io.ktor:ktor-io-jvm")
    // Dependency with out-dated version (actual one is resolved from BOM)
    implementation("io.ktor:ktor-client-core-jvm:2.3.8")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

publishing {
    repositories {
        maven {
            url = uri("https://packages.jetbrains.team/maven/p/amper/amper")
            credentials {
                username = "Alexey.Barsov"
                password = "define-actual-token-here"
            }
            name = "space"
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}