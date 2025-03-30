/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.kmpp

import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleInvalidPathSource
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.IosSettings
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.trySet
import org.jetbrains.amper.gradle.EntryPointType
import org.jetbrains.amper.gradle.FragmentWrapper
import org.jetbrains.amper.gradle.LeafFragmentWrapper
import org.jetbrains.amper.gradle.base.AmperNamingConventions
import org.jetbrains.amper.gradle.base.BindingPluginPart
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.closureSources
import org.jetbrains.amper.gradle.findEntryPoint
import org.jetbrains.amper.gradle.java.javaMainSourceSet
import org.jetbrains.amper.gradle.java.javaTestSourceSet
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.amperFragment
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSet
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSetName
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.mostCommonNearestAmperFragment
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.target
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.targetCompilation
import org.jetbrains.amper.gradle.kotlin.configureCompilerOptions
import org.jetbrains.amper.gradle.kotlin.configureFrom
import org.jetbrains.amper.gradle.layout
import org.jetbrains.amper.gradle.replacePenultimatePaths
import org.jetbrains.amper.gradle.tryAdd
import org.jetbrains.amper.gradle.tryRemove
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.Family
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.relativeTo


// Introduced function to remember to propagate language settings.
context(KMPEAware)
fun KotlinSourceSet.doDependsOn(it: FragmentWrapper) {
    val dependency = it.kotlinSourceSet
    dependsOn(dependency ?: return)
}

private fun LanguageSettingsBuilder.configureFromAmperSettings(settings: KotlinSettings) {
    languageVersion = settings.languageVersion.schemaValue
    apiVersion = settings.apiVersion.schemaValue
    progressiveMode = settings.progressiveMode
    settings.languageFeatures?.forEach { enableLanguageFeature(it.value.capitalized()) }
    settings.optIns?.forEach { optIn(it.value) }
}

/**
 * Plugin logic, bind to specific module, when multiple targets are available.
 */
context(ProblemReporterContext)
class KMPPBindingPluginPart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx, KMPEAware, AmperNamingConventions {

    internal val fragmentsByKotlinSourceSetName = mutableMapOf<String, FragmentWrapper>()

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    override val needToApply = true

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun applyBeforeEvaluate() {
        initTargets()
        initSourceSets()

        // Workaround for KTIJ-27212, to get proper compiler arguments in the common code facet after import.
        // Apparently, configuring compiler arguments for metadata compilation is not sufficient.
        // This workaround doesn't fix intermediate source sets, though, only the most common fragment.
        kotlinMPE.compilerOptions {
            configureFrom(module.mostCommonFragment.settings)
        }

        val hasJUnit5 = module.fragments
            .any { it.settings.junit == JUnitVersion.JUNIT5 }
        val hasJUnit = module.fragments
            .any { it.settings.junit != JUnitVersion.NONE }
        if (hasJUnit) {
            project.configurations.all { config ->
                config.resolutionStrategy.capabilitiesResolution.withCapability("org.jetbrains.kotlin:kotlin-test-framework-impl") {
                    val selected = if (hasJUnit5) {
                        it.candidates
                            .filter { (it.id as? ModuleComponentIdentifier)?.module == "kotlin-test-junit5" }
                            .firstOrNull { (it.id as? ModuleComponentIdentifier)?.version == UsedVersions.kotlinVersion }
                            ?: error("No kotlin test junit5 dependency variant on classpath. Existing: " +
                                    it.candidates.joinToString { it.variantName })
                    } else {
                        it.candidates
                            .firstOrNull { (it.id as? ModuleComponentIdentifier)?.version == UsedVersions.kotlinVersion }
                            ?: error("No kotlin test junit dependency variant on classpath. Existing: " +
                                    it.candidates.joinToString { it.variantName })
                    }

                    it.select(selected)
                    it.because("Select junit impl, since it is embedded")
                }
            }
        }
    }

    override fun applyAfterEvaluate() {
        // IOS Compose uses UiKit, so we need to explicitly enable it, since it is experimental.
        project.extraProperties.set("org.jetbrains.compose.experimental.uikit.enabled", "true")

        // Do after fragments init!
        adjustSourceSetDirectories()
        project.afterEvaluate {
            // We need to do that second time, because of tricky gradle/KMPP/android stuff.
            //
            // First call is needed because we need to search for entry point.
            // Second call is needed, because we need to rewrite changes from KMPP that
            // are done in "afterEvaluate" also.
            adjustSourceSetDirectories()
        }
    }

    /**
     * Set Amper specific directory layout.
     */
    private fun adjustSourceSetDirectories() {
        kotlinMPE.sourceSets.all { sourceSet ->
            val fragment = sourceSet.amperFragment
            when {
                // Do GRADLE_JVM specific.
                layout == Layout.GRADLE_JVM -> {
                    if (sourceSet.name == "jvmMain") {
                        replacePenultimatePaths(sourceSet.kotlin, sourceSet.resources, "main")
                    } else if (sourceSet.name == "jvmTest") {
                        replacePenultimatePaths(sourceSet.kotlin, sourceSet.resources, "test")
                    }
                }

                // Do AMPER specific.
                layout == Layout.AMPER && fragment != null -> {
                    // We need to remove directories that the Kotlin plugin adds by default, while still respecting
                    // potential source roots added by some third-party plugin. This is impossible to track per se, so
                    // we instead approximate this by relying on the fact that most third parties use the Provider type
                    // to add such source roots, while the Kotlin plugin uses File and Path. The Kotlin plugin also uses
                    // FileCollection to automatically add the source roots registered on Java source sets, hence why we
                    // check specifically for this one (and remove it).
                    // Once things are cleaned up, we add the directories that the Amper layout cares about.
                    sourceSet.kotlin
                        .tryRemove { it is File || it is Path || it.isJavaSourceDirectoriesCollection() }
                        .tryAdd(fragment.src)
                    sourceSet.resources.tryRemove { it is File || it is Path }.tryAdd(fragment.resourcesPath)
                }

                layout == Layout.AMPER && fragment == null -> {
                    sourceSet.kotlin.setSrcDirs(emptyList<File>())
                    sourceSet.resources.setSrcDirs(emptyList<File>())
                }
            }
        }
    }

    /**
     * Whether this is a [org.gradle.api.file.FileCollection] coming from a Java source set.
     */
    private fun Any.isJavaSourceDirectoriesCollection(): Boolean {
        val javaSourceDirectories = listOfNotNull(
            project.javaMainSourceSet?.java?.sourceDirectories,
            project.javaTestSourceSet?.java?.sourceDirectories,
        )
        return javaSourceDirectories.any { it === this }
    }

    fun afterAll() {
        project.afterEvaluate {
            // Need after evaluate to catch up android compilations.
            module.artifactPlatforms.forEach { platform ->
                val patchedCompilations = mutableListOf<KotlinCompilation<*>>()

                module.leafFragments.filter { it.platform == platform }.forEach { fragment ->
                    fragment.targetCompilation?.apply {
                        compileTaskProvider.configureCompilerOptions(fragment.settings)
                        patchedCompilations.add(this)
                    }
                }

                // This doesn't seem to actually fix intermediate fragments.
                // TODO check if it is useful at all
                platform.target?.compilations?.forEach loop@{ compilation ->
                    if (compilation in patchedCompilations) return@loop
                    val nearestFragment = compilation.defaultSourceSet.mostCommonNearestAmperFragment
                    val settings = nearestFragment?.settings ?: return@loop

                    compilation.compileTaskProvider.configureCompilerOptions(settings)
                }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    private fun initTargets() = with(KotlinAmperNamingConvention) {
        module.artifactPlatforms.filter { it.isLeaf }.forEach {
            val targetName = it.targetName
            when (it) {
                Platform.ANDROID -> kotlinMPE.androidTarget(targetName)
                Platform.JVM -> kotlinMPE.jvm(targetName)
                Platform.IOS_ARM64 -> kotlinMPE.iosArm64(targetName)
                Platform.IOS_SIMULATOR_ARM64 -> kotlinMPE.iosSimulatorArm64(targetName)
                Platform.IOS_X64 -> kotlinMPE.iosX64(targetName)
                Platform.MACOS_X64 -> kotlinMPE.macosX64(targetName)
                Platform.MACOS_ARM64 -> kotlinMPE.macosArm64(targetName)
                Platform.LINUX_X64 -> kotlinMPE.linuxX64(targetName)
                Platform.LINUX_ARM64 -> kotlinMPE.linuxArm64(targetName)
                // TODO Currently no nodejs mode.
                Platform.JS -> kotlinMPE.js(targetName) { browser() }
                // TODO Currently no nodejs mode.
                Platform.WASM -> kotlinMPE.wasmJs(targetName) { browser() }
                Platform.TVOS_ARM64 -> kotlinMPE.tvosArm64(targetName)
                Platform.TVOS_X64 -> kotlinMPE.tvosX64(targetName)
                Platform.TVOS_SIMULATOR_ARM64 -> kotlinMPE.tvosSimulatorArm64(targetName)
                Platform.WATCHOS_ARM64 -> kotlinMPE.watchosArm64(targetName)
                Platform.WATCHOS_ARM32 -> kotlinMPE.watchosArm32(targetName)
                Platform.WATCHOS_DEVICE_ARM64 -> kotlinMPE.watchosDeviceArm64(targetName)
                Platform.WATCHOS_SIMULATOR_ARM64 -> kotlinMPE.watchosSimulatorArm64(targetName)
                Platform.MINGW_X64 -> kotlinMPE.mingwX64(targetName)
                Platform.ANDROID_NATIVE_ARM32 -> kotlinMPE.androidNativeArm32(targetName)
                Platform.ANDROID_NATIVE_ARM64 -> kotlinMPE.androidNativeArm64(targetName)
                Platform.ANDROID_NATIVE_X64 -> kotlinMPE.androidNativeX64(targetName)
                Platform.ANDROID_NATIVE_X86 -> kotlinMPE.androidNativeX86(targetName)

                // These are not leaf platforms, thus - should not get here.
                Platform.ANDROID_NATIVE, Platform.MINGW, Platform.WATCHOS,
                Platform.IOS, Platform.MACOS, Platform.TVOS, Platform.APPLE,
                Platform.LINUX, Platform.NATIVE, Platform.COMMON -> Unit
            }
        }

        // Skip tests binary creation for now.
        module.leafFragments.forEach { fragment ->
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
        "org.jetbrains.kotlin.amper.target.variant",
        String::class.java
    )

    private fun adjust(
        target: KotlinNativeTarget,
        kotlinNativeCompilation: KotlinNativeCompilation,
        fragment: LeafFragmentWrapper,
    ) {
        val iosSettings = fragment.settings.ios
        val nativeSettings = fragment.settings.native

        target.binaries {
            when {
                (module.type == ProductType.IOS_APP && !fragment.isTest) -> framework(fragment.name) {
                    adjustExecutable(fragment, kotlinNativeCompilation)
                }

                fragment.isTest -> test(fragment.name) {
                    adjustExecutable(fragment, kotlinNativeCompilation)
                }

                (module.type == ProductType.LIB && !fragment.isTest) ->
                    adjustLib(target, iosSettings, fragment)

                (module.type == ProductType.LIB && fragment.isTest) -> return@binaries

                else -> executable(fragment.name) {
                    adjustExecutable(fragment, kotlinNativeCompilation)
                    project.afterEvaluate {
                        // Check if entry point was not set in build script.
                        if (entryPoint == null) {
                            entryPoint = if (nativeSettings?.entryPoint != null) {
                                nativeSettings.entryPoint
                            } else {
                                val sources = kotlinNativeCompilation.defaultSourceSet.closureSources
                                findEntryPoint(sources, EntryPointType.NATIVE, LoggerFactory.getLogger("main-finder"))
                            }
                        }
                    }
                }
            }
        }
        // workaround to have a few variants of the same darwin target
        if (!module.type.isLibrary()) kotlinNativeCompilation.attributes {
            attribute(
                disambiguationVariantAttribute,
                target.name
            )
        }
    }

    private fun adjustLib(
        target: KotlinNativeTarget,
        settings: IosSettings?,
        fragment: LeafFragmentWrapper,
    ) {
        if (!module.type.isLibrary() || fragment.isTest) return

        if (target.konanTarget.family == Family.IOS) {
            target.binaries {
                framework(settings?.framework?.basename ?: module.userReadableName) {
                    isStatic = settings?.framework?.isStatic ?: false
                }
            }
        }
    }

    private fun NativeBinary.adjustExecutable(
        fragment: LeafFragmentWrapper,
        kotlinNativeCompilation: KotlinNativeCompilation,
    ) {
//        println("FOO basename is ${fragment.settings.ios?.framework?.basename}")
        compilation = kotlinNativeCompilation
        fragment.settings.ios.framework.let {
            ::baseName trySet it.basename
//            ::optimized trySet it.optimized
//            binaryOptions.putAll(it.binaryOptions)
        }
        fragment.settings.kotlin.let {
            linkerOpts.addAll(it.linkerOpts.orEmpty().map { it.value })
            ::debuggable trySet it.debug
        }
    }

    private fun initSourceSets() = with(KotlinAmperNamingConvention) {
        // First iteration - create source sets and add dependencies.
        module.fragments.forEach { fragment ->
            fragment.maybeCreateSourceSet {
                fragmentsByKotlinSourceSetName[name] = fragment
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
                                    compile && !runtime && exported -> error("Exporting a compile-only dependency is not supported")
                                    !compile && runtime && exported -> error("Cannot export a runtime-only dependency to the consumer's compile classpath")
                                    !compile && !runtime -> error("At least one scope of (compile, runtime) must be declared")
                                    else -> KotlinDependencyHandler::implementation
                                }
                                tmp
                            } else {
                                { implementation(it) }
                            }
                        when (externalDependency) {
                            is MavenDependencyBase -> depFunction(externalDependency.coordinates.value)
                            is LocalModuleDependency -> {
                                val source = externalDependency.module.source
                                if (source is AmperModuleInvalidPathSource) {
                                    val relativeInvalidPath = source.invalidPath.relativeTo(project.projectDir.toPath())
                                    throw GradleException(
                                        "Unresolved dependency in project '${project.path}': " +
                                                "cannot find Amper module at '$relativeInvalidPath'")
                                }
                                depFunction(externalDependency.module.linkedProject)
                            }
                            else -> error("Unsupported dependency type: $externalDependency")
                        }
                    }
                }
            }
        }

        val adjustedSourceSets = mutableSetOf<KotlinSourceSet>()

        // Second iteration - set language settings
        module.fragments.forEach { fragment ->
            val sourceSets = fragment.matchingKotlinSourceSets

            for (sourceSet in sourceSets) {
                // Apply language settings.
                sourceSet.languageSettings.configureFromAmperSettings(fragment.settings.kotlin)
                adjustedSourceSets.add(sourceSet)
            }
        }

        val commonKotlinSettings = module.mostCommonFragment.settings.kotlin

        // we imply, sourceSets which was not touched by loop by fragments, they depend only on common
        // to avoid gradle incompatibility error between sourceSets we apply to sourceSets left settings from common
        (kotlinMPE.sourceSets.toSet() - adjustedSourceSets).forEach { sourceSet ->
            sourceSet.languageSettings.configureFromAmperSettings(commonKotlinSettings)
        }

        // it is implied newly added sourceSets will depend on common
        kotlinMPE.sourceSets
            .matching { !adjustedSourceSets.contains(it) }
            .configureEach { sourceSet ->
                sourceSet.languageSettings.configureFromAmperSettings(commonKotlinSettings)
            }

        module.fragments.forEach { fragment ->
            // TODO Replace with inner classes structure for wrappers.
            with(module) {
                val sourceSet = fragment.kotlinSourceSet
                // Set dependencies.
                fragment.fragmentDependencies.forEach {
                    when (it.type) {
                        FragmentDependencyType.REFINE ->
                            sourceSet?.doDependsOn(it.target.wrapped)

                        FragmentDependencyType.FRIEND ->
                            // TODO Add associate with for related compilations.
                            // Not needed for default "test" - "main" relations.
                            run { }
                    }
                }
            }
        }

        // Third iteration - adjust kotlin prebuilt source sets (UNMANAGED ones)
        // to match created ones.
        module.leafFragments.forEach { fragment ->
            val compilationSourceSet = fragment.targetCompilation?.defaultSourceSet ?: return@forEach
            if (compilationSourceSet != fragment.kotlinSourceSet) {
                // Add dependency from compilation source set ONLY for unmanaged source sets.
                if (compilationSourceSet.amperFragment == null) {
                    compilationSourceSet.dependsOn(fragment.kotlinSourceSet ?: return@with)
                }
            }
        }
    }

    // ------
    private fun FragmentWrapper.maybeCreateSourceSet(
        block: KotlinSourceSet.() -> Unit,
    ) {
        val sourceSet = kotlinMPE.sourceSets.maybeCreate(kotlinSourceSetName)
        sourceSet.block()
    }
}

private val AmperModule.mostCommonFragment: Fragment
    get() = fragments.firstOrNull { fragment ->
        fragment.fragmentDependencies.none { it.type == FragmentDependencyType.REFINE }
    } ?: error("Couldn't find the most common fragment")
