package org.jetbrains.deft.proto.gradle.java

import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.BindPlatform
import org.jetbrains.deft.proto.gradle.BindingPluginPart
import org.jetbrains.deft.proto.gradle.PluginPartCtx
import org.jetbrains.deft.proto.gradle.part
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun applyJavaAttributes(ctx: PluginPartCtx) = JavaBindingPluginPart(ctx).apply()

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class JavaBindingPluginPart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("some-logger")
    }

    private val javaAPE: JavaApplication = project.extensions.getByType(JavaApplication::class.java)

    fun apply() {
        applyJavaApplication()
    }

    private fun applyJavaApplication() {
        val jvmArtifacts = module.artifacts
            .filter { Platform.JVM in it.platforms }
            .filter { it.part<JavaApplicationArtifactPart>() != null }
        if (jvmArtifacts.size > 1)
            logger.warn("Cant apply multiple settings for application plugin. " +
                    "Affected artifacts: ${jvmArtifacts.joinToString { it.name }}. " +
                    "Applying application settings from first one."
            )
        val artifact = jvmArtifacts.firstOrNull() ?: return
        val applicationSettings = artifact.part<JavaApplicationArtifactPart>()!!
        javaAPE.apply {
            mainClass.set(applicationSettings.mainClass)
        }
    }

//    private fun adjustSourceSets() {
//
//    }
}