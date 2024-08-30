/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.compilation.kotlinModuleName
import org.jetbrains.amper.compilation.mergedCompilationSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.toRepositories
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setFragments
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.ksp.Ksp
import org.jetbrains.amper.ksp.KspCommonConfig
import org.jetbrains.amper.ksp.KspCompilationType
import org.jetbrains.amper.ksp.KspConfig
import org.jetbrains.amper.ksp.KspJsConfig
import org.jetbrains.amper.ksp.KspJvmConfig
import org.jetbrains.amper.ksp.KspNativeConfig
import org.jetbrains.amper.ksp.KspOutputPaths
import org.jetbrains.amper.ksp.WebBackend
import org.jetbrains.amper.ksp.downloadKspJars
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.settings.unanimousSetting
import org.jetbrains.amper.tasks.AdditionalClasspathProvider
import org.jetbrains.amper.tasks.AdditionalResourcesProvider
import org.jetbrains.amper.tasks.AdditionalResourcesProvider.ResourceRoot
import org.jetbrains.amper.tasks.AdditionalSourcesProvider
import org.jetbrains.amper.tasks.AdditionalSourcesProvider.SourceRoot
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.identificationPhrase
import org.jetbrains.amper.tasks.jvm.JvmCompileTask
import org.jetbrains.amper.tasks.native.NativeCompileKlibTask
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.executeForFiles
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString

internal class KspTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val isTest: Boolean,
    private val fragments: List<Fragment>,
    private val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
): Task {
    private val mavenResolver = MavenResolver(userCacheRoot)

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val kspVersion = fragments.unanimousSetting("ksp.version") { it.ksp.version }
        val kspJars = downloadKspCli(kspVersion)
        val ksp = Ksp(kspVersion, jdk, kspJars)

        val kspProcessorJars = resolveKspProcessorsFor(fragments)

        val externalDependencies = dependenciesResult.filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath }
            .filter { it.extension != "aar" } // we get the extracted classes instead from an AdditionalClasspathProvider

        // TODO in the future split those in 2 groups: dependencies for the modules, and local ksp processors.
        //   We can use the list of ksp processors and filter the task results by module name.
        val compileJvmModuleDependencies = dependenciesResult.filterIsInstance<JvmCompileTask.Result>()
            .map { it.classesOutputRoot }
        val compileNativeModuleDependencies = dependenciesResult.filterIsInstance<NativeCompileKlibTask.Result>()
            .flatMap { it.dependencyKlibs + listOf(it.compiledKlib) }
        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProvider>()
            .flatMap { it.compileClasspath }
        val compileLibraries = externalDependencies + compileJvmModuleDependencies + compileNativeModuleDependencies + additionalClasspath

        // In the KSP Gradle plugin, the cache and output roots are unique per target and per default source set of the
        // compilation (so, jvm/jvmMain, jvm/jvmTest, ...). In our case, each KSP task is already per compilation, so we
        // can use the task output root as a base for caches and outputs.
        val kspOutputs = KspOutputPaths(
            cachesDir = taskOutputRoot.path / "ksp-cache",
            outputsBaseDir = taskOutputRoot.path / "ksp-generated",
            moduleBaseDir = module.source.moduleDir ?: taskOutputRoot.path,
        )
        ksp.runKsp(
            compileLibraries = compileLibraries,
            kspOutputPaths = kspOutputs,
            kspProcessorJars = kspProcessorJars,
            // TODO make sure we use the same JDK as for Kotlin compilation when it becomes customizable
            kotlinCompilationJdkHome = jdk.homeDir,
        )

        val leafFragmentName = fragments.find { it.platforms == setOf(platform) }?.name
            ?: error("Cannot find leaf fragment for platform $platform")

        return Result(
            sourceRoots = listOf(kspOutputs.javaSourcesDir, kspOutputs.kotlinSourcesDir)
                .map { SourceRoot(leafFragmentName, it) },
            resourceRoots = listOf(ResourceRoot(leafFragmentName, kspOutputs.resourcesDir)),
            compileClasspath = listOf(kspOutputs.classesDir),
        )
    }

    private suspend fun downloadKspCli(kspVersion: String): List<Path> {
        val kspDownloadConfiguration = mapOf("kspVersion" to kspVersion)
        return executeOnChangedInputs.executeForFiles("download-ksp-cli-$kspVersion", kspDownloadConfiguration, emptyList()) {
            spanBuilder("download-ksp-cli")
                .setAttribute("ksp-version", kspVersion)
                .useWithScope {
                    mavenResolver.downloadKspJars(kspVersion)
                }
        }
    }

    private suspend fun resolveKspProcessorsFor(fragments: List<Fragment>): List<Path> {
        val repositories = module.mavenRepositories.filter { it.resolve }.map { it.url }.distinct()

        // TODO handle catalog references and module dependencies
        val processorCoords = fragments.flatMap { it.settings.ksp.processors }.map { it.value }.distinct()

        val configuration = mapOf(
            "userCacheRoot" to userCacheRoot.path.pathString,
            "repositories" to repositories.joinToString("|"),
            "fragments" to fragments.joinToString("|") { it.name },
            "processors" to processorCoords.joinToString("|"),
        )

        return executeOnChangedInputs.executeForFiles("${taskName.name}-resolve-ksp-processors", configuration, emptyList()) {
            spanBuilder("resolve-ksp-processors")
                .setAmperModule(module)
                .setFragments(fragments)
                .setAttribute("platform", platform.pretty)
                .setListAttribute("ksp-processors", processorCoords)
                .useWithScope {
                    mavenResolver.resolve(
                        coordinates = processorCoords,
                        repositories = repositories.toRepositories(),
                        platform = ResolutionPlatform.JVM,
                        scope = ResolutionScope.RUNTIME,
                        resolveSourceMoniker = "KSP processors for ${module.userReadableName}-${platform.pretty}",
                    )
                }
        }
    }

    private suspend fun Ksp.runKsp(
        compileLibraries: List<Path>,
        kspOutputPaths: KspOutputPaths,
        kspProcessorJars: List<Path>,
        kotlinCompilationJdkHome: Path,
    ) {
        val compilationSettings = fragments.mergedCompilationSettings()
        val kspCompilationType = KspCompilationType.forPlatform(platform)
        val sources = fragments.map { it.src }.filter { it.exists() }
        val sharedConfig: KspConfig.Builder.() -> Unit = {
            moduleName = module.kotlinModuleName(isTest = isTest)

            if (platform == Platform.COMMON) {
                commonSourceRoots = sources
            } else {
                sourceRoots = sources
            }

            libraries = compileLibraries
            processorOptions = fragments.flatMap { it.settings.ksp.processorOptions.entries }
                .associate { it.key.value to it.value.value }
        }
        val kspConfig = when (kspCompilationType) {
            KspCompilationType.Common -> KspCommonConfig(kspOutputPaths, compilationSettings) { sharedConfig() }
            KspCompilationType.JVM -> KspJvmConfig(kspOutputPaths, compilationSettings) {
                sharedConfig()
                javaSourceRoots = sources
                jdkHome = kotlinCompilationJdkHome
            }
            KspCompilationType.JS -> KspJsConfig(kspOutputPaths, compilationSettings) {
                sharedConfig()
                backend = when (platform) {
                    Platform.WASM -> WebBackend.WASM
                    Platform.JS -> WebBackend.JS
                    else -> error("Unsupported platform $platform for KSP JS compilation")
                }
            }
            KspCompilationType.Native -> KspNativeConfig(kspOutputPaths, compilationSettings) {
                sharedConfig()
                targetName = platform.schemaValue

                // TODO should we add stdlib and platform libs manually like in the KSP Gradle plugin?
                // https://github.com/google/ksp/blob/e1b8468309aeff7912420a202300751783d0b2c9/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L474-L486
            }
        }
        val configuration = mapOf(
            "kspVersion" to this.kspVersion,
            "kspCompilationType" to kspCompilationType.name,
            "kspProcessorJars" to kspProcessorJars.joinToString("|"),
            "kspConfig" to kspConfig.toCommandLineOptions(kspConfig.projectBaseDir).joinToString(" "),
        )
        val inputFiles = kspProcessorJars + sources + compileLibraries

        executeOnChangedInputs.executeForFiles("${taskName.name}-run-ksp", configuration, inputFiles) {
            cleanDirectory(kspOutputPaths.outputsBaseDir)
            if (sources.isEmpty()) {
                logger.info("No sources were found for ${fragments.identificationPhrase()}, skipping KSP")
            } else {
                logger.info("Running KSP on ${fragments.identificationPhrase()}")
                run(
                    compilationType = kspCompilationType,
                    processorJars = kspProcessorJars,
                    config = kspConfig,
                    tempRoot = tempRoot,
                )
            }
            listOf(kspOutputPaths.outputsBaseDir)
        }
    }

    class Result(
        override val sourceRoots: List<SourceRoot>,
        override val resourceRoots: List<ResourceRoot>,
        override val compileClasspath: List<Path>,
    ) : TaskResult, AdditionalSourcesProvider, AdditionalResourcesProvider, AdditionalClasspathProvider

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(KspTask::class.java)
    }
}
