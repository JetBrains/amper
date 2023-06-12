plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    @Suppress("OPT_IN_USAGE") targetHierarchy.default()
    jvm()

    sourceSets {
        @Suppress("UNUSED_VARIABLE") val commonTest by getting {
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
}