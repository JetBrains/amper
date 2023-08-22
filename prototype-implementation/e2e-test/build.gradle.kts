deft.useDeftLayout = true

kotlin {
    sourceSets {
        val commonTest by sourceSets.getting {
            dependencies {
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
    maxHeapSize = "4g"
}
