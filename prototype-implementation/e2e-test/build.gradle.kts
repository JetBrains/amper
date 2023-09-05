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

private val pluginClasspathDir = "pluginUnderTestMetadata"

tasks.findByName("jvmTestProcessResources")?.apply {
    this as AbstractCopyTask
    dependsOn(":gradle-integration:copyDescriptorsHack")
    from(project(":gradle-integration").buildDir.resolve(pluginClasspathDir))
}
