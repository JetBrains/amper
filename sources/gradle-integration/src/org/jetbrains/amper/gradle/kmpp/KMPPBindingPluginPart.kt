/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.kmpp

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.SourceDirectorySet
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModuleDependency
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
import org.jetbrains.amper.gradle.java.JavaBindingPluginPart
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.amperFragment
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.compilation
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSet
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSetName
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.mostCommonNearestAmperFragment
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.target
import org.jetbrains.amper.gradle.layout
import org.jetbrains.amper.gradle.replacePenultimatePaths
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.konan.target.Family
import java.io.File
import java.nio.file.Path


// Introduced function to remember to propagate language settings.
context(KMPEAware)
fun KotlinSourceSet.doDependsOn(it: FragmentWrapper) {
    val dependency = it.kotlinSourceSet
    dependsOn(dependency ?: return)
}

private fun KotlinSourceSet.doApplyPart(settings: KotlinSettings?) = languageSettings.apply {
    settings ?: return@apply
    languageVersion = settings.languageVersion.schemaValue
    apiVersion = settings.apiVersion?.schemaValue
    if (progressiveMode != settings.progressiveMode) progressiveMode = settings.progressiveMode
    settings.languageFeatures?.forEach { enableLanguageFeature(it.capitalized()) }
    settings.optIns?.forEach { optIn(it) }
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

    override fun applyBeforeEvaluate() {
        initTargets()
        initFragments()
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
                    sourceSet.kotlin.replaceByAmperLayout(fragment.src) { it.endsWith("kotlin") }
                    sourceSet.resources.replaceByAmperLayout(fragment.resourcesPath) { it.endsWith("resources") }
                }

                layout == Layout.AMPER && fragment == null -> {
                    sourceSet.kotlin.setSrcDirs(emptyList<File>())
                    sourceSet.resources.setSrcDirs(emptyList<File>())
                }
            }
        }
    }

    private fun SourceDirectorySet.replaceByAmperLayout(
        toAdd: Path,
        toRemove: (File) -> Boolean
    ) {
        val delegate = ReflectionSourceDirectorySet.tryWrap(this)
        if (delegate == null) {
            // Here we can't delete anything, since values can be provided by
            // Gradle's [Provider] and so, add new dependencies between source sets and tasks.
            srcDir(toAdd)
            return
        } else with(delegate.mutableSources) {
            removeIf { (it as? File)?.let(toRemove) == true }
            if (!contains(toAdd)) add(toAdd)
        }
    }

    fun afterAll() {
        project.afterEvaluate {
            // Need after evaluate to catch up android compilations.
            module.artifactPlatforms.forEach { platform ->
                with(platform.target ?: return@forEach) {
                    val patchedCompilations = mutableListOf<KotlinCompilation<*>>()
                    module.leafFragments.filter { it.platform == platform }.forEach { fragment ->
                        fragment.compilation?.apply {
                            fragment.settings.kotlin.let {
                                kotlinOptions::allWarningsAsErrors trySet it.allWarningsAsErrors
                                kotlinOptions::freeCompilerArgs trySet it.freeCompilerArgs
                                kotlinOptions::suppressWarnings trySet it.suppressWarnings
                                kotlinOptions::verbose trySet it.verbose
                            }

                            // Set jvm target for all jvm like compilations.
                            val selectedJvmTarget = fragment.settings.jvm?.target
                            if (selectedJvmTarget != null) {
                                compileTaskProvider.configure {
                                    it.compilerOptions.apply {
                                        if (this !is KotlinJvmCompilerOptions) return@apply
                                        this.jvmTarget.set(JvmTarget.fromTarget(selectedJvmTarget.schemaValue))
                                    }
                                }
                            }

                            patchedCompilations.add(this)
                        }
                    }

                    compilations.forEach loop@{ compilation ->
                        if (compilation in patchedCompilations) return@loop
                        val nearestFragment = compilation.defaultSourceSet
                            .mostCommonNearestAmperFragment

                        val foundJvmTarget = nearestFragment
                            ?.settings?.jvm?.target ?: return@loop

                        compilation.compileTaskProvider.configure {
                            it.compilerOptions.apply {
                                if (this !is KotlinJvmCompilerOptions) return@apply
                                this.jvmTarget.set(JvmTarget.fromTarget(foundJvmTarget.schemaValue))
                            }
                        }
                    }
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
                                findEntryPoint(sources, EntryPointType.NATIVE, JavaBindingPluginPart.logger)
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
            linkerOpts.addAll(it.linkerOpts.orEmpty())
            ::debuggable trySet it.debug
        }
    }

    private fun initFragments() = with(KotlinAmperNamingConvention) {
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
                                model.module.map { depFunction(it.linkedProject) }
                            }

                            else -> error("Unsupported dependency type: $externalDependency")
                        }
                    }

                    // Add a separator to WA kotlin-test bug: https://youtrack.jetbrains.com/issue/KT-60913/Rework-kotlin-test-jvm-variant-inference
                    // Add implicit tests dependencies.
                    if (fragment.isTest) {
                        if (fragment.platforms.all { it == Platform.JVM || it == Platform.ANDROID }) {
                            when (fragment.settings.junit) {
                                JUnitVersion.JUNIT5 -> {
                                    implementation("org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}")
                                    implementation("org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}")
                                    implementation("org.junit.jupiter:junit-jupiter-api:${UsedVersions.junit5Version}")
                                    implementation("org.junit.jupiter:junit-jupiter-engine:${UsedVersions.junit5Version}")
                                }

                                JUnitVersion.JUNIT4 -> {
                                    implementation("org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}")
                                    implementation("org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}")
                                    implementation("junit:junit:${UsedVersions.junit4Version}")
                                }

                                null, JUnitVersion.NONE -> Unit
                            }
                        } else {
                            implementation("org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}")
                        }
                    }
                }
            }
        }

        val adjustedSourceSets = mutableSetOf<KotlinSourceSet>()

        // Second iteration - create dependencies between fragments (aka source sets) and set source/resource directories.
        module.fragments.forEach { fragment ->
            val sourceSets = fragment.matchingKotlinSourceSets

            for (sourceSet in sourceSets) {
                // Apply language settings.
                sourceSet.doApplyPart(fragment.settings.kotlin)
                adjustedSourceSets.add(sourceSet)
            }
        }

        // we imply, sourceSets which was not touched by loop by fragments, they depend only on common
        // to avoid gradle incompatibility error between sourceSets we apply to sourceSets left settings from common
        val commonKotlinSettings = module.fragments
            .firstOrNull { it.fragmentDependencies.none { it.type == FragmentDependencyType.REFINE } }
            ?.settings?.kotlin

        (kotlinMPE.sourceSets.toSet() - adjustedSourceSets).forEach { sourceSet ->
            commonKotlinSettings?.let {
                sourceSet.doApplyPart(it)
            }
        }

        // it is implied newly added sourceSets will depend on common
        kotlinMPE.sourceSets
            .matching { !adjustedSourceSets.contains(it) }
            .configureEach { sourceSet ->
                commonKotlinSettings?.let {
                    sourceSet.doApplyPart(it)
                }
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
            val target = fragment.target ?: return@forEach
            with(target) {
                val compilation = fragment.compilation ?: return@forEach
                val compilationSourceSet = compilation.defaultSourceSet
                if (compilationSourceSet != fragment.kotlinSourceSet) {
                    // Add dependency from compilation source set ONLY for unmanaged source sets.
                    if (compilationSourceSet.amperFragment == null) {
                        compilationSourceSet.dependsOn(fragment.kotlinSourceSet ?: return@with)
                    }
                }
            }
        }

        // TODO: AMPER-189 workaround remove after KTIJ-27212 will be fixed
        module.leafFragments.forEach { fragment ->
            fragment.settings.kotlin
                .freeCompilerArgs
                ?.filter { it.startsWith("-X") }
                ?.map { it.substring(2) }
                ?.map {
                    it.split("-").joinToString("") { it.capitalized() }
                }
                ?.forEach {
                    val target = fragment.target ?: return@forEach
                    with(target) {
                        val compilation = fragment.compilation ?: return@forEach
                        val compilationSourceSet = compilation.defaultSourceSet
                        compilationSourceSet.languageSettings.enableLanguageFeature(it)
                    }
                    fragment.kotlinSourceSet?.languageSettings?.enableLanguageFeature(it)
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
