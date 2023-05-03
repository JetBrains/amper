package org.jetbrains.deft.proto.gradle.kmpp

import org.gradle.api.attributes.Attribute
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.*
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
) : BindingPluginPart by ctx {

    private val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        initTargets()
        initFragments()
    }

    private fun initTargets() {
        module.artifacts.forEach { artifact ->
            artifact.bindPlatforms.forEach { bPlatform ->
                val targetName = bPlatform.targetName
                val platform = bPlatform.platform
                check(platform.isLeaf) { "Artifacts can't contain non leaf targets. Non leaf target: $platform" }
                when (platform) {
                    Platform.ANDROID -> kotlinMPE.android(targetName)
                    Platform.JVM -> kotlinMPE.jvm(targetName) { /*withJava()*/ }
                    Platform.IOS_ARM64 -> kotlinMPE.iosArm64(targetName)  { adjust(artifact) }
                    Platform.IOS_SIMULATOR_ARM64 -> kotlinMPE.iosSimulatorArm64(targetName) { adjust(artifact) }
                    Platform.IOS_X64 -> kotlinMPE.iosX64(targetName) { adjust(artifact) }
                    Platform.MACOS_ARM64 -> kotlinMPE.macosArm64(targetName) { adjust(artifact) }
                    Platform.JS -> kotlinMPE.js(targetName)
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

    private fun initFragments() {
        // Introduced function to remember to propagate language settings.
        fun KotlinSourceSet.doDependsOn(it: Fragment) {
            val wrapper = it as? FragmentWrapper ?: FragmentWrapper(it)
            applyOtherFragmentsPartsRecursively(it)
            System.err.println("DEPEND FROM $name ON ${it.name}")
            dependsOn(wrapper.sourceSet)
        }

        // Clear sources and resources for existing source sets.
        val existingSourceSets = kotlinMPE.sourceSets.toList()
        existingSourceSets.forEach {
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
            val sourceSet = fragment.sourceSet

            // Apply language settings.
            sourceSet.applyOtherFragmentsPartsRecursively(fragment)

            // Set dependencies.
            fragment.fragmentDependencies.forEach {
                sourceSet.doDependsOn(it.target)
            }

            // Set sources and resources.
            sourceSet.kotlin.srcDirs.clear()
            val srcDir = fragment.src ?: fragment.srcPath
            println(srcDir)
            sourceSet.kotlin.srcDir(srcDir)
            sourceSet.resources.srcDirs.clear()
            sourceSet.resources.srcDir("${fragment.src ?: fragment.srcPath}/resources")
        }

        // Third iteration - adjust kotlin prebuilt source sets to match created ones.
        println("EXISING ARTIFACTS: ${module.artifacts.joinToString { it.name }}")
        module.artifacts.forEach { artifact ->
            println("ADJUSTING EXISING ARTIFACT: $artifact")
            artifact.bindPlatforms.forEach inner@{ platform ->
                val compilation = platform.compilation ?: return@inner
                artifact.fragments.forEach {
                    compilation.defaultSourceSet.doDependsOn(it)
                }
            }
        }

        module.fragments.forEach { fragment ->
            val possiblePrebuiltName = "${fragment.name}Main"
            findSourceSet(possiblePrebuiltName)?.doDependsOn(fragment)
        }

        project.configurations.map {
            val c = it
            it.attributes {
                it.attribute(Attribute.of("artifactName", String::class.java), c.name)
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
        languageVersion = kotlinPart.languageVersion ?: "1.8"
        apiVersion = kotlinPart.apiVersion ?: "1.8"
        if (progressiveMode != (kotlinPart.progressiveMode ?: false)) progressiveMode =
            kotlinPart.progressiveMode ?: false
        kotlinPart.languageFeatures.forEach { enableLanguageFeature(it) }
        kotlinPart.optIns.forEach { optIn(it) }
    }

    // ------
    private val Fragment.resourcesPath get() = path.resolve("resources")
    private fun findSourceSet(name: String) = kotlinMPE.sourceSets.findByName(name)
    private val Fragment.sourceSet get() = kotlinMPE.sourceSets.getByName(name)
    private fun Fragment.maybeCreateSourceSet(block: KotlinSourceSet.() -> Unit) {
        val sourceSet = kotlinMPE.sourceSets.maybeCreate(name)
        sourceSet.block()
    }

    // Convenient agreements on naming and accessing targets and compilations.
    private val BindPlatform.targetName: String get() =
        if (artifact is TestArtifact) BindPlatform(platform, artifact.testFor).targetName
        else "${artifact.name}${platform.prettySuffix}"
    private val BindPlatform.target get() = kotlinMPE.targets.findByName(targetName)
    private val BindPlatform.compilationName get() = when {
        artifact is TestArtifact && artifact.testFor.name == artifact.name -> "test"
        artifact is TestArtifact -> artifact.name
        else -> "main"
    }
    private val BindPlatform.compilation get() = target?.compilations?.findByName(compilationName)

}