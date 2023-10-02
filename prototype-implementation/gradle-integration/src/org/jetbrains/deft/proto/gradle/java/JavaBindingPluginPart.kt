package org.jetbrains.deft.proto.gradle.java

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.deft.proto.frontend.ComposePart
import org.jetbrains.deft.proto.frontend.JavaPart
import org.jetbrains.deft.proto.frontend.JvmPart
import org.jetbrains.deft.proto.frontend.Layout
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.*
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.java.JavaDeftNamingConvention.deftFragment
import org.jetbrains.deft.proto.gradle.java.JavaDeftNamingConvention.maybeCreateJavaSourceSet
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSet
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class JavaBindingPluginPart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.JVM), KMPEAware, DeftNamingConventions {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("some-logger")
    }

    private val javaAPE: JavaApplication? get() = project.extensions.findByType(JavaApplication::class.java)
    internal val javaPE: JavaPluginExtension get() = project.extensions.getByType(JavaPluginExtension::class.java)

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    override val needToApply by lazy { Platform.JVM in module }

    override fun applyBeforeEvaluate() {
        applyJavaTargetForKotlin()

        if (Platform.ANDROID in module) {
            logger.warn(
                "Cant enable java integration when android is enabled. " +
                        "Module: ${module.userReadableName}"
            )
            return
        }

        adjustJavaGeneralProperties()
        addJavaIntegration()
    }

    override fun applyAfterEvaluate() {
        adjustSourceDirs()
    }

    private fun applyJavaTargetForKotlin() = with(KotlinDeftNamingConvention) {
        leafPlatformFragments.forEach { fragment ->
            with(fragment.target!!) {
                fragment.parts.find<JvmPart>()?.target?.let { jvmTarget ->
                    fragment.compilation?.compileTaskProvider?.configure {
                        it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                        it.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmTarget))
                    }
                }
            }
        }
    }

    private fun adjustJavaGeneralProperties() {
        if (leafPlatformFragments.size > 1)
        // TODO Add check that all parts values are the same instead of this approach.
            logger.info(
                "Cant apply multiple settings for application plugin. " +
                        "Affected artifacts: ${platformArtifacts.joinToString { it.name }}. " +
                        "Applying application settings from first one."
            )
        val fragment = leafPlatformFragments.firstOrNull() ?: return
        if (fragment.parts.find<ComposePart>()?.enabled != true) {
            project.plugins.apply(ApplicationPlugin::class.java)
        }

        val jvmPart = fragment.parts.find<JvmPart>()
        val javaSource = fragment.parts.find<JavaPart>()?.source ?: jvmPart?.target
        jvmPart?.target?.let {
            javaPE.targetCompatibility = JavaVersion.toVersion(it)
        }
        javaSource?.let {
            javaPE.sourceCompatibility = JavaVersion.toVersion(it)
        }
        // Do when layout is known.
        project.afterEvaluate {
            if (module.type.isLibrary()) return@afterEvaluate
            val foundMainClass = if (jvmPart?.mainClass != null) {
                jvmPart.mainClass
            } else {
                val sources = fragment.kotlinSourceSet?.closureSources ?: emptyList()
                findEntryPoint(sources, EntryPointType.JVM, logger)
            }

            javaAPE?.apply {
                // Check if main class is set in the build script.
                if (mainClass.orNull == null) {
                    mainClass.set(foundMainClass)
                }
            }
            // TODO Handle deft variants gere, when we will switch to manual java source sets creation.
            project.tasks.withType(Jar::class.java) {
                it.manifest {
                    it.attributes["Main-Class"] = foundMainClass
                }
            }
        }
    }


    // TODO Rewrite this completely by not calling
    //  KMPP code and following out own conventions.
    private fun addJavaIntegration() {
        project.plugins.apply(JavaPlugin::class.java)

        kotlinMPE.targets.toList().forEach {
            if (it is KotlinJvmTarget) it.withJava()
        }

        // Set sources for all deft related source sets.
        platformFragments.forEach {
            it.maybeCreateJavaSourceSet()
        }
    }

    private fun adjustSourceDirs() {
        javaPE.sourceSets.all { sourceSet ->
            val fragment = sourceSet.deftFragment
            when {
                // Do GRADLE_JVM specific.
                layout == Layout.GRADLE_JVM -> {
                    if (sourceSet.name == "main") {
                        replacePenultimatePaths(sourceSet.java, sourceSet.resources, "main")
                    } else if (sourceSet.name == "test") {
                        replacePenultimatePaths(sourceSet.java, sourceSet.resources, "test")
                    }
                }

                // Do DEFT specific.
                layout == Layout.DEFT && fragment != null -> {
                    sourceSet.java.setSrcDirs(listOf(fragment.src))
                    sourceSet.resources.setSrcDirs(listOf(fragment.resourcesPath))
                }
                layout == Layout.DEFT && fragment == null -> {
                    sourceSet.java.setSrcDirs(emptyList<File>())
                    sourceSet.resources.setSrcDirs(emptyList<File>())
                }
            }
        }
    }
}