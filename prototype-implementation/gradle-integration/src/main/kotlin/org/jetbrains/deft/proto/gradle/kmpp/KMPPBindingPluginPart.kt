package org.jetbrains.deft.proto.gradle.kmpp

import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
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
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
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
        val part = artifact.part<NativeApplicationArtifactPart>()
        binaries {
            executable {
                entryPoint = part?.entryPoint
            }
        }
    }

    private fun initFragments(): List<Configuration> {
        val isAndroid = module.fragments.any { it.platforms.contains(Platform.ANDROID) }
        val aware = if (isAndroid) androidAware else javaAware
        return with(aware) {
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
                    println("Sourceset name for dependency ${this.name}")
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
                        val compilation = artifact.compilation ?: return@inner
                        val compilationSourceSet = compilation.defaultSourceSet
                        if (compilationSourceSet != fragment.kotlinSourceSet) {
                            compilationSourceSet.doDependsOn(fragment)
                        }
                    }
                }
            }

            project.configurations.map {
                val c = it
                it.attributes {
                    it.attribute(Attribute.of("artifactName", String::class.java), c.name)
                }
            }

        }
    }

    private fun KotlinSourceSet.applyOtherFragmentsPartsRecursively(
        from: Fragment
    ): LanguageSettingsBuilder = languageSettings.apply {
        val wrapper = from as? FragmentWrapper ?: FragmentWrapper(from)
        doApplyPart(wrapper.part<KotlinFragmentPart>())
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