amper.useAmperLayout = true

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

    dependsOn(":sources:gradle-integration:jar")
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
    dependsOn(":sources:gradle-integration:copyDescriptorsHack")
    dependsOn(":sources:gradle-integration:pluginUnderTestMetadata")
    from(project(":sources:gradle-integration").buildDir.resolve(pluginClasspathDir))
}
