package org.jetbrains.deft.proto.gradle.java

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.deft.proto.frontend.JavaArtifactPart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.java.JavaDeftNamingConvention.maybeCreateJavaSourceSet
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.target
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun applyJavaAttributes(ctx: PluginPartCtx) = JavaBindingPluginPart(ctx).apply()

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class JavaBindingPluginPart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.JVM), KMPEAware, DeftNamingConventions {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("some-logger")
    }

    private val javaAPE: JavaApplication get() = project.extensions.getByType(JavaApplication::class.java)
    internal val javaPE: JavaPluginExtension get() = project.extensions.getByType(JavaPluginExtension::class.java)

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        project.plugins.apply(ApplicationPlugin::class.java)
        applyJavaApplication()
        adjustJavaSourceSets()
    }

    private fun applyJavaApplication() {
        val jvmArtifacts = module.artifacts
            .filter { Platform.JVM in it.platforms }
            .filter { it.parts.find<JavaArtifactPart>() != null }
            .filter { !it.isTest }
        if (jvmArtifacts.size > 1)
            logger.warn(
                "Cant apply multiple settings for application plugin. " +
                        "Affected artifacts: ${jvmArtifacts.joinToString { it.name }}. " +
                        "Applying application settings from first one."
            )
        val artifact = jvmArtifacts.firstOrNull() ?: return
        val applicationSettings = artifact.parts.find<JavaArtifactPart>()!!
        javaAPE.apply {
            mainClass.set(applicationSettings.mainClass)
        }

        artifact.parts.find<JavaArtifactPart>()?.jvmTarget?.let {
            javaPE.targetCompatibility = JavaVersion.toVersion(it)
        }

        project.tasks.withType(KotlinCompile::class.java).configureEach {
            it.javaPackagePrefix = applicationSettings.packagePrefix
        }
    }


    // TODO Rewrite this completely by not calling
    //  KMPP code and following out own conventions.
    private fun adjustJavaSourceSets() {
        project.plugins.apply(JavaPlugin::class.java)

        val kotlinJvmTarget = Platform.JVM.target as? KotlinJvmTarget
        kotlinJvmTarget?.withJava()

        // Set sources for all deft related source sets.
        platformFragments.forEach {
            it.maybeCreateJavaSourceSet {
                java.setSrcDirs(it.sourcePaths)
                resources.setSrcDirs(it.resourcePaths)
            }
        }
    }
}