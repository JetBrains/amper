/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.fragmentsTargeting
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.identificationPhrase
import org.jetbrains.amper.tasks.ios.ManageXCodeProjectTask
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString

internal class NativeLinkTask(
    override val module: AmperModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    override val buildType: BuildType,
    val compilationType: KotlinCompilationType,
    /**
     * The name of the task that produces the klib for the sources of this module.
     */
    val compileKLibTaskName: TaskName,
    /**
     * Task names that produce klibs that need to be exposed as API in the resulting artifact.
     */
    val exportedKLibTaskNames: Set<TaskName>,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
        require(compilationType != KotlinCompilationType.LIBRARY)
    }

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): Result {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        val externalKLibs = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath } // runtime dependencies including transitive
            .distinct()
            .filterKLibs()
            .toList()

        val includeArtifactDependency = dependenciesResult
            .filterIsInstance<NativeCompileKlibTask.Result>()
            .firstOrNull { it.taskName == compileKLibTaskName }
            ?: error("The result of the klib compilation task (${compileKLibTaskName.name}) was not found")
        val includeArtifact = includeArtifactDependency.compiledKlib
        if (includeArtifact == null && isTest) {
            // We may skip linking for test specifically if there's no compiled code in the fragments.
            // Libraries are of no interest here because they can't contain any tests
            logger.info("No test code was found compiled for ${fragments.identificationPhrase()}, skipping linking")
            return Result(
                linkedBinary = null,
            )
        }

        val compileKLibDependencies = dependenciesResult
            .filterIsInstance<NativeCompileKlibTask.Result>()
            .filter { it.taskName != compileKLibTaskName }

        val exportedKLibDependencies = compileKLibDependencies
            .filter { it.taskName in exportedKLibTaskNames }
        check(exportedKLibDependencies.size == exportedKLibTaskNames.size)

        val compileKLibs = compileKLibDependencies.mapNotNull { it.compiledKlib }
        val exportedKLibs = exportedKLibDependencies.mapNotNull { it.compiledKlib }

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        logger.debug("native link '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val entryPoints = if (module.type.isApplication()) {
            fragments.mapNotNull { it.settings.native?.entryPoint }.distinct()
        } else emptyList()
        if (entryPoints.size > 1) {
            // TODO raise this error in the frontend?
            userReadableError("Multiple entry points defined in ${fragments.identificationPhrase()}:\n${entryPoints.joinToString("\n")}")
        }
        val entryPoint = entryPoints.singleOrNull()

        val binaryOptions = if (compilationType == KotlinCompilationType.IOS_FRAMEWORK) {
            val appBundleId = dependenciesResult.requireSingleDependency<ManageXCodeProjectTask.Result>()
                .debugResolvedXcodeSettings.bundleId
            // Format framework's bundleId based on app's bundleId
            val frameworkBundleId = "$appBundleId.kotlin.framework"
            logger.debug("Using framework bundleId: `$frameworkBundleId`")
            mapOf("bundleId" to frameworkBundleId)
        } else emptyMap()

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "entry.point" to (entryPoint ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
            "binary.options" to Json.encodeToString(binaryOptions),
        )

        val inputs = listOfNotNull(includeArtifact) + compileKLibs
        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            if (isTest) {
                logger.debug("Linking native test executable for module '${module.userReadableName}' on platform '${platform.pretty}'...")
            } else {
                val binaryKind = when(compilationType) {
                    KotlinCompilationType.IOS_FRAMEWORK -> "framework"
                    else -> "executable"
                }
                if (inputs.isEmpty()) {
                    val fragmentsString = module.fragmentsTargeting(platform, includeTestFragments = false)
                        .identificationPhrase()
                    userReadableError("Unable to link: " +
                            "there are no inputs (libraries or compiled source code). " +
                            "Ensure that there are sources and/or dependencies for $fragmentsString")
                }
                logger.info("Linking native '${platform.pretty}' $binaryKind for module '${module.userReadableName}'...")
            }

            val artifactPath = taskOutputRoot.path.resolve(compilationType.outputFilename(module, platform, isTest))

            val nativeCompiler = downloadNativeCompiler(kotlinVersion, userCacheRoot)
            val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(
                plugins = kotlinUserSettings.compilerPlugins,
            )
            val args = kotlinNativeCompilerArgs(
                buildType = buildType,
                kotlinUserSettings = kotlinUserSettings,
                compilerPlugins = compilerPlugins,
                entryPoint = entryPoint,
                libraryPaths = compileKLibs + externalKLibs,
                exportedLibraryPaths = exportedKLibs,
                // no need to pass fragments nor sources, we only build from klibs
                fragments = emptyList(),
                sourceFiles = emptyList(),
                additionalSourceRoots = emptyList(),
                outputPath = artifactPath,
                compilationType = compilationType,
                binaryOptions = binaryOptions,
                include = includeArtifact,
            )

            nativeCompiler.compile(args, tempRoot, module)

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifactPath))
        }.outputs.single()

        return Result(
            linkedBinary = artifact,
        )
    }

    class Result(
        val linkedBinary: Path?,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
