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
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"

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

private val pluginClasspathDir = "pluginUnderTestMetadata"

tasks.findByName("jvmTestProcessResources")?.apply {
    this as AbstractCopyTask
    dependsOn(":gradle-integration:copyDescriptorsHack")
    dependsOn(":gradle-integration:pluginUnderTestMetadata")
    from(project(":gradle-integration").buildDir.resolve(pluginClasspathDir))
}
