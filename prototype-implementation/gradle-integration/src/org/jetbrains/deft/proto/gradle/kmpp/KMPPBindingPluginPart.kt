package org.jetbrains.deft.proto.gradle.kmpp

import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.*
import org.jetbrains.deft.proto.gradle.android.AndroidAwarePart
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.java.JavaBindingPluginPart
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.compilation
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.deftFragment
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSet
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSetName
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.target
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File

fun applyKotlinMPAttributes(ctx: PluginPartCtx) = KMPPBindingPluginPart(ctx).apply()

/**
 * Plugin logic, bind to specific module, when multiple targets are available.
 */
class KMPPBindingPluginPart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx, KMPEAware, DeftNamingConventions {

    private val androidAware = AndroidAwarePart(ctx)
    private val javaAware = JavaBindingPluginPart(ctx)

    internal val fragmentsByName = module.fragments.associateBy { it.name }

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        initTargets()
        initFragments()
    }

    private fun initTargets() = with(KotlinDeftNamingConvention) {
        module.nonTestArtifacts.forEach { artifact ->
            artifact.fragments.forEach { fragment ->
                val platform = fragment.platforms.singleOrNull()
                    ?: error("Leaf fragment must have exactly one platform!")
                check(platform.isLeaf) { "Artifacts can't contain non leaf targets. Non leaf target: $platform" }
                // FIXME Support variants: create multiple compilations - one compilation for
                // FIXME each leaf fragment.
                when (platform) {
                    Platform.ANDROID -> kotlinMPE.android()
                    Platform.JVM -> kotlinMPE.jvm()
                    Platform.IOS_ARM64 -> kotlinMPE.iosArm64() { adjust(artifact) }
                    Platform.IOS_SIMULATOR_ARM64 -> kotlinMPE.iosSimulatorArm64() { adjust(artifact) }
                    Platform.IOS_X64 -> kotlinMPE.iosX64() { adjust(artifact) }
                    Platform.MACOS_ARM64 -> kotlinMPE.macosArm64() { adjust(artifact) }
                    Platform.JS -> kotlinMPE.js()
                    else -> error("Unsupported platform: $platform")
                }
            }
        }
    }

    private fun KotlinNativeTarget.adjust(artifact: ArtifactWrapper) {
        if (module.type != PotatoModuleType.APPLICATION) return
        val part = artifact.parts.find<NativeApplicationArtifactPart>()
        binaries {
            executable(artifact.name) {
                entryPoint = part?.entryPoint
            }
        }
    }

    private fun initFragments() {
        val isAndroid = module.fragments.any { it.platforms.contains(Platform.ANDROID) }
        val aware = if (isAndroid) androidAware else javaAware
        with(aware) {
            // Introduced function to remember to propagate language settings.
            fun KotlinSourceSet.doDependsOn(it: Fragment) {
                val wrapper = it as? FragmentWrapper ?: FragmentWrapper(it)
                applyOtherFragmentsPartsRecursively(it)
                wrapper.kotlinSourceSet
                dependsOn(wrapper.kotlinSourceSet ?: return)
            }

            // Clear sources and resources for non created by us source sets.
            // Can be called after project evaluation.
            kotlinMPE.sourceSets.all {
                if (it.deftFragment != null) return@all
                it.kotlin.setSrcDirs(emptyList<File>())
                it.resources.setSrcDirs(emptyList<File>())
            }

            // First iteration - create source sets and add dependencies.
            module.fragments.forEach { fragment ->
                fragment.maybeCreateSourceSet {
                    dependencies {
                        fragment.externalDependencies.forEach { externalDependency ->
                            when (externalDependency) {
                                is MavenDependency -> implementation(externalDependency.coordinates)
                                is PotatoModuleDependency -> with(externalDependency) {
                                    implementation(model.module.linkedProject)
                                }

                                else -> error("Unsupported dependency type: $externalDependency")
                            }
                        }
                    }
                }
            }

            // Second iteration - create dependencies between fragments (aka source sets) and set source/resource directories.
            module.fragments.forEach { fragment ->
                val sourceSet = fragment.kotlinSourceSet ?: return@forEach

                // Apply language settings.
                sourceSet.applyOtherFragmentsPartsRecursively(fragment)

                // Set dependencies.
                fragment.fragmentDependencies.forEach {
                    sourceSet.doDependsOn(it.target)
                }

                // Set sources and resources.
                sourceSet.kotlin.setSrcDirs(fragment.sourcePaths)
                sourceSet.resources.setSrcDirs(fragment.resourcePaths)
            }

            // Third iteration - adjust kotlin prebuilt source sets to match created ones.
            module.artifacts.forEach { artifact ->
                artifact.fragments.forEach inner@{ fragment ->
                    val platform = fragment.platforms
                        .requireSingle { "Leaf fragment must contain exactly single platform!" }
                    val target = platform.target ?: return@inner
                    with(target) {
                        // setting jvmTarget for android (as android compilations are appeared after project evaluation,
                        // also their names does not match with our artifact names)
                        if (platform == Platform.ANDROID) {
                            artifact.parts.find<JavaArtifactPart>()?.jvmTarget?.let { jvmTarget ->
                                project.afterEvaluate {
                                    val androidTarget = kotlinMPE.targets.findByName("android") ?: return@afterEvaluate
                                    val compilations = if (artifact is TestArtifact) {
                                        androidTarget.compilations.matching { it.name.lowercase().contains("test") }
                                    } else {
                                        androidTarget.compilations.matching { !it.name.lowercase().contains("test") }
                                    }
                                    compilations.configureEach {
                                        it.compileTaskProvider.configure {
                                            it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                                            it.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmTarget))
                                        }
                                    }
                                }
                            }
                        }
                        // settings jvmTarget for other jvm compilations
                        val compilation = artifact.compilation ?: return@inner
                        if (platform == Platform.JVM) {
                            artifact.parts.find<JavaArtifactPart>()?.jvmTarget?.let { jvmTarget ->
                                compilation.compileTaskProvider.configure {
                                    it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                                    it.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmTarget))
                                }
                            }
                        }
                        val compilationSourceSet = compilation.defaultSourceSet
                        if (compilationSourceSet != fragment.kotlinSourceSet) {
                            compilationSourceSet.doDependsOn(fragment)
                        }
                    }
                }
            }
        }
    }

    private fun KotlinSourceSet.applyOtherFragmentsPartsRecursively(
        from: Fragment
    ): LanguageSettingsBuilder = languageSettings.apply {
        val wrapper = from as? FragmentWrapper ?: FragmentWrapper(from)
        doApplyPart(wrapper.parts.find<KotlinFragmentPart>())
        from.fragmentDependencies.forEach {
            applyOtherFragmentsPartsRecursively(it.target)
        }
    }

    private fun KotlinSourceSet.doApplyPart(kotlinPart: KotlinFragmentPart?) = languageSettings.apply {
        // TODO Propagate properly.
        kotlinPart ?: return@apply
        // TODO Change defaults to some merge chain. Now languageVersion checking ruins build.
        languageVersion = kotlinPart.languageVersion
        apiVersion = kotlinPart.apiVersion
        if (progressiveMode != (kotlinPart.progressiveMode ?: false)) progressiveMode =
            kotlinPart.progressiveMode ?: false
        kotlinPart.languageFeatures.forEach { enableLanguageFeature(it.capitalized()) }
        kotlinPart.optIns.forEach { optIn(it) }
    }

    // ------
    internal fun findSourceSet(name: String) = kotlinMPE.sourceSets.findByName(name)

    context (SpecificPlatformPluginPart)
    private fun FragmentWrapper.maybeCreateSourceSet(
        block: KotlinSourceSet.() -> Unit
    ) {
        val sourceSet = kotlinMPE.sourceSets.maybeCreate(kotlinSourceSetName)
        sourceSet.block()
    }

}