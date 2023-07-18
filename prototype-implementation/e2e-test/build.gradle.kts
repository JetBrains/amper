plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        commonTest.configure {
            kotlin.srcDirs("src/")
            resources.srcDirs("resources/")
            dependencies {
                implementation(kotlin("test"))
                implementation(dependencies.gradleTestKit())
            }
        }
    }
}

tasks.withType<Test> {
    dependsOn(":gradle-integration:jar")
    useJUnitPlatform()
    val inBootstrapMode: String? by project
    inBootstrapMode?.let {
        if (it == "true") {
            filter {
                excludeTestsMatching("*BootstrapTest*")
            }
        }
    }
}
