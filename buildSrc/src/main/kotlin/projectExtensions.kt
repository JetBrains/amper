import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata


fun Project.amperGradlePlugin() {
    // A workaround about hard coded "java" software component, that
    // is used in gradle development plugin.
    afterEvaluate {
        project.extensions.getByType(PublishingExtension::class.java).apply {
            publications.findByName("pluginMaven")?.let {
                publications.remove(it)
                publications.maybeCreate("pluginMavenDecorated", MavenPublication::class.java).apply {
                    from(components.getByName("kotlin"))
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
    val copyDescriptorsHack = tasks.register("copyDescriptorsHack", Copy::class.java) {
        dependsOn("pluginDescriptors")
        from("build/pluginDescriptors")
        destinationDir = file("build/pluginDescriptorsHack/META-INF/gradle-plugins")
    }

    // Do not change the task name!
    tasks.findByName("jvmProcessResources")?.apply {
        this as AbstractCopyTask
        dependsOn("pluginDescriptors")
        dependsOn(copyDescriptorsHack)
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
    tasks.configureEach {
        if (name != "distTar" && name != "distZip") return@configureEach
        this as AbstractCopyTask
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
