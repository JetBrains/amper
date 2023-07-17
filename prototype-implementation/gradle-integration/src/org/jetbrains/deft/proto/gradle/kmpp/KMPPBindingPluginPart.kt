package org.jetbrains.deft.proto.gradle.kmpp

import org.gradle.api.attributes.Attribute
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.FoundEntryPoint
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.LeafFragmentWrapper
import org.jetbrains.deft.proto.gradle.base.*
import org.jetbrains.deft.proto.gradle.findEntryPoint
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSet
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention.kotlinSourceSetName
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractExecutable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File

fun applyKotlinMPAttributes(ctx: PluginPartCtx) = KMPPBindingPluginPart(ctx).apply()


// Introduced function to remember to propagate language settings.
context(KMPEAware, SpecificPlatformPluginPart)
fun KotlinSourceSet.doDependsOn(it: Fragment) {
    val wrapper = it as? FragmentWrapper ?: FragmentWrapper(it)
    val dependency = wrapper.kotlinSourceSet
    dependsOn(dependency ?: return)
}

private fun KotlinSourceSet.doApplyPart(kotlinPart: KotlinPart?) = languageSettings.apply {
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

    private val androidAware = SpecificPlatformPluginPart(ctx, Platform.ANDROID)
    private val javaAware = SpecificPlatformPluginPart(ctx, Platform.JVM)
    private val noneAware = NoneAwarePart(ctx)

    internal val fragmentsByName = module.fragments.associateBy { it.name }

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        initTargets()
        initFragments()
    }

    private fun initTargets() = with(KotlinDeftNamingConvention) {
        module.artifactPlatforms.forEach {
            val targetName = it.targetName
            when (it) {
                Platform.ANDROID -> kotlinMPE.android(targetName)
                Platform.JVM -> kotlinMPE.jvm(targetName)
                Platform.IOS_ARM64 -> kotlinMPE.iosArm64(targetName)
                Platform.IOS_SIMULATOR_ARM64 -> kotlinMPE.iosSimulatorArm64(targetName)
                Platform.IOS_X64 -> kotlinMPE.iosX64(targetName)
                Platform.MACOS_ARM64 -> kotlinMPE.macosArm64(targetName)
                Platform.JS -> kotlinMPE.js(targetName)
                else -> error("Unsupported platform: $targetName")
            }
        }

        // Skip tests binary creation for now.
        module.leafFragments.filter { !it.isTest }.forEach { fragment ->
            val target = fragment.target ?: return@forEach
            with(target) target@{
                if (fragment.platform != Platform.ANDROID) {
                    fragment.maybeCreateCompilation {
                        if (this@target is KotlinNativeTarget)
                            adjust(
                                this@target,
                                this as KotlinNativeCompilation,
                                fragment
                            )
                    }
                }
            }
        }
    }

    private val disambiguationVariantAttribute = Attribute.of(
        "org.jetbrains.kotlin.deft.target.variant",
        String::class.java
    )

    private fun adjust(
        target: KotlinNativeTarget,
        kotlinNativeCompilation: KotlinNativeCompilation,
        fragment: LeafFragmentWrapper,
    ) {
        fun FoundEntryPoint.native() = if (pkg != null) "$pkg.main" else "main"
        if (module.type != PotatoModuleType.APPLICATION) return
        val part = fragment.parts.find<NativeApplicationPart>()

        target.binaries {
            when {
                fragment.isTest -> test(fragment.name) {
                    adjustExecutable(part, kotlinNativeCompilation)
                }
                else -> executable(fragment.name) {
                    adjustExecutable(part, kotlinNativeCompilation)
                    entryPoint = part?.entryPoint
                        ?: findEntryPoint("main.kt", fragment).native()
                }
            }
        }
        // workaround to have a few variants of the same darwin target
        kotlinNativeCompilation.attributes {
            attribute(
                disambiguationVariantAttribute,
                target.name
            )
        }
    }

    private fun AbstractExecutable.adjustExecutable(
        part: NativeApplicationPart?,
        kotlinNativeCompilation: KotlinNativeCompilation
    ) {
        part ?: return
        ::baseName trySet part.baseName
        ::debuggable trySet part.debuggable
        ::optimized trySet part.optimized
        binaryOptions.putAll(part.binaryOptions)
        linkerOpts.addAll(part.linkerOpts)
        compilation = kotlinNativeCompilation
    }

    private fun initFragments() = with(KotlinDeftNamingConvention) {
        val isAndroid = module.fragments.any { it.platforms.contains(Platform.ANDROID) }
        val isJava = module.fragments.any { it.platforms.contains(Platform.JVM) }
        val aware = if (isAndroid) androidAware else if (isJava) javaAware else noneAware
        with(aware) aware@{
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
                sourceSet.doApplyPart(fragment.parts.find<KotlinPart>())

                // Set dependencies.
                fragment.fragmentDependencies.forEach {
                    sourceSet.doDependsOn(it.target)
                }

                // Set sources and resources.
                sourceSet.kotlin.setSrcDirs(fragment.sourcePaths)
                sourceSet.resources.setSrcDirs(fragment.resourcePaths)
            }

            // Third iteration - adjust kotlin prebuilt source sets to match created ones.
            module.leafFragments.forEach { fragment ->
                val platform = fragment.platform
                val target = fragment.target ?: return@forEach
                with(target) {
                    // setting jvmTarget for android (as android compilations are appeared after project evaluation,
                    // also their names do not match with our artifact names)
                    if (platform == Platform.ANDROID) {
                        // FIXME ??
                    }
                    val compilation = fragment.compilation ?: return@forEach
                    // settings jvmTarget for other jvm compilations
                    if (platform == Platform.JVM) {
                        fragment.parts.find<JvmPart>()?.jvmTarget?.let { jvmTarget ->
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

    // ------
    context (SpecificPlatformPluginPart)
    private fun FragmentWrapper.maybeCreateSourceSet(
        block: KotlinSourceSet.() -> Unit
    ) {
        val sourceSet = kotlinMPE.sourceSets.maybeCreate(kotlinSourceSetName)
        sourceSet.block()
    }
}