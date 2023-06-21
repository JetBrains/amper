package org.jetbrains.deft.proto.gradle.kmpp

import org.gradle.api.attributes.Attribute
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.ArtifactWrapper
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.android.AndroidAwarePart
import org.jetbrains.deft.proto.gradle.base.*
import org.jetbrains.deft.proto.gradle.java.JavaBindingPluginPart
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSet
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSetName
import org.jetbrains.deft.proto.gradle.requireSingle
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File

fun applyKotlinMPAttributes(ctx: PluginPartCtx) = KMPPBindingPluginPart(ctx).apply()


// Introduced function to remember to propagate language settings.
context(KMPEAware, SpecificPlatformPluginPart)
fun KotlinSourceSet.doDependsOn(it: Fragment) {
    val wrapper = it as? FragmentWrapper ?: FragmentWrapper(it)
    applyOtherFragmentsPartsRecursively(it)
    val dependency = wrapper.kotlinSourceSet
    dependsOn(dependency ?: return)
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

/**
 * Plugin logic, bind to specific module, when multiple targets are available.
 */
class KMPPBindingPluginPart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx, KMPEAware, DeftNamingConventions {

    private val androidAware = AndroidAwarePart(ctx)
    private val javaAware = JavaBindingPluginPart(ctx)
    private val noneAware = NoneAwarePart(ctx)

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
                val platform = fragment.platforms.single()
                check(platform.isLeaf) { "Artifacts can't contain non leaf targets. Non leaf target: $platform" }
                // FIXME Support variants: create multiple compilations - one compilation for
                // FIXME each leaf fragment.
                val targetName = getTargetName(fragment, platform)
                when (platform) {
                    Platform.ANDROID ->
                        kotlinMPE.android(targetName)
                    Platform.JVM ->
                        kotlinMPE.jvm(targetName)
                    Platform.IOS_ARM64 ->
                        kotlinMPE.iosArm64(targetName) { adjust(artifact, targetName) }
                    Platform.IOS_SIMULATOR_ARM64 ->
                        kotlinMPE.iosSimulatorArm64(targetName) { adjust(artifact, targetName) }
                    Platform.IOS_X64 ->
                        kotlinMPE.iosX64(targetName) { adjust(artifact, targetName) }
                    Platform.MACOS_ARM64 ->
                        kotlinMPE.macosArm64(targetName) { adjust(artifact, targetName) }
                    Platform.JS ->
                        kotlinMPE.js(targetName)
                    else -> error("Unsupported platform: $platform")
                }
            }
        }
    }

    private fun KotlinNativeTarget.adjust(artifact: ArtifactWrapper, targetName: String) {
        if (module.type != PotatoModuleType.APPLICATION) return
        val part = artifact.parts.find<NativeApplicationArtifactPart>()
        binaries {
            executable(artifact.name) {
                entryPoint = part?.entryPoint
            }
        }
        // workaround to have a few variants of the same darwin target
        attributes {
            attribute(Attribute.of("org.jetbrains.kotlin.target.variant", String::class.java), artifact.name)
        }
        println("${targetName}CInteropApiElements" + " value ${artifact.name}")
        project.configurations.findByName("${targetName}CInteropApiElements")?.let {
            it.attributes {
                it.attribute(
                    Attribute.of("org.jetbrains.kotlin.target.variant", String::class.java),
                    artifact.name
                )
            }
        }
        project.configurations.findByName("${targetName}MetadataElements")?.let {
            it.attributes {
                it.attribute(
                    Attribute.of("org.jetbrains.kotlin.target.variant", String::class.java),
                    artifact.name
                )
            }
        }
    }

    private fun initFragments() = with(KotlinDeftNamingConvention) {
        val isAndroid = module.fragments.any { it.platforms.contains(Platform.ANDROID) }
        val isJava = module.fragments.any { it.platforms.contains(Platform.JVM) }
        val aware = if (isAndroid) androidAware else if (isJava) javaAware else noneAware
        with(aware) {
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
                            val depFunction: KotlinDependencyHandler.(Any) -> Unit =
                                if (externalDependency is DefaultScopedNotation) with(externalDependency) {
                                    // tmp variable fixes strangely red code with "ambiguity"
                                    val tmp: KotlinDependencyHandler.(Any) -> Unit = when {
                                        compile && runtime && !exported -> KotlinDependencyHandler::implementation
                                        !compile && runtime && !exported -> KotlinDependencyHandler::runtimeOnly
                                        compile && !runtime && !exported -> KotlinDependencyHandler::compileOnly
                                        compile && runtime && exported -> KotlinDependencyHandler::api
                                        compile && !runtime && exported -> error("Not supported")
                                        !compile && runtime && exported -> error("Not supported")
                                        !compile && !runtime -> error("At least one scope of (compile, runtime) must be declared")
                                        else -> KotlinDependencyHandler::implementation
                                    }
                                    tmp
                                } else {
                                    { implementation(it) }
                                }
                            when (externalDependency) {
                                is MavenDependency -> depFunction(externalDependency.coordinates)
                                is PotatoModuleDependency -> with(externalDependency) {
                                    depFunction(model.module.linkedProject)
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
                    val targetName = getTargetName(fragment, platform)
                    val target = kotlinMPE.targets.findByName(targetName) ?: return@inner
                    with(target) {
                        // setting jvmTarget for android (as android compilations are appeared after project evaluation,
                        // also their names do not match with our artifact names)
                        if (platform == Platform.ANDROID) {

                        }
                        val compilation = artifact.compilation ?: return@inner
                        // settings jvmTarget for other jvm compilations
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