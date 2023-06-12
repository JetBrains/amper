// Don't remove this comment! It is parsed.
// plugin org.gradle.java-gradle-plugin
extensions.findByType(GradlePluginDevelopmentExtension::class.java)?.apply {
    plugins {
        create("deftProtoSettingsPlugin") {
            id = "org.jetbrains.deft.proto.settings.plugin"
            implementationClass = "org.jetbrains.deft.proto.gradle.BindingSettingsPlugin"
        }
    }
}

// A workaround about hard coded "java" software component, that
// is used in gradle development plugin.
afterEvaluate {
    extensions.findByType(PublishingExtension::class.java)!!.apply {
        publications.findByName("pluginMaven")?.let {
            publications.remove(it)
            publications.maybeCreate("pluginMavenDecorated", MavenPublication::class.java).apply {
                from(components["kotlin"])
            }
        }
    }
    tasks.filter { it.name.contains("PluginMaven") }.forEach {
        it.enabled = false
    }
}

//
// A workaround to fix KMPP resources handling.
//
// KMMP logic is as follows:
// 1. KMPP creates its own process resources task for each compilation
// 2. KMPP also uses resources from java source set
//
// So, if resources are created directly in build directory by some
// third party plugin (like gradle-plugin), so they remain
// "invisible" to KMPP process resources task (since it looks only
// at sources).
//
// So, I have to copy such resources manually.
//
val copyDescriptorsHack = tasks.create<Copy>("copyDescriptorsHack") {
    dependsOn("pluginDescriptors")
    from("build/pluginDescriptors")
    destinationDir = file("build/pluginDescriptorsHack/META-INF/gradle-plugins")
}

tasks.findByName("mainJvmProcessResources")!!.apply {
    dependsOn("pluginDescriptors")
    dependsOn("copyDescriptorsHack")
    this as AbstractCopyTask
    from(file("build/pluginDescriptorsHack"))
}

// Add also this tests classes as plugin classpath for running plugin.
tasks.withType<PluginUnderTestMetadata>().configureEach {
    dependsOn("compileTestKotlinJvm")
    dependsOn("processTestResources")
    pluginClasspath.setFrom(
            pluginClasspath
                    .plus(files(project.buildDir.resolve("classes/kotlin/jvm/test")))
                    // See upper comment about hack.
                    .plus(files(project.buildDir.resolve("processedResources/jvm/main")))
                    .plus(files(project.buildDir.resolve("processedResources/jvm/test")))
    )
}

// Workaround for somehow appearing duplicates.
tasks.all {
    if (name != "distTar" && name != "distZip") return@all
    this as AbstractCopyTask
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
