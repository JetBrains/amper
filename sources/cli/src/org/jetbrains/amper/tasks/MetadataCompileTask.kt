/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.kotlinMetadataCompilerArgs
import org.jetbrains.amper.compilation.kotlinModuleName
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.friends
import org.jetbrains.amper.frontend.refinedFragments
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jvm.Jdk
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.walk

class MetadataCompileTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    private val fragment: Fragment,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): ArtifactTaskBase(), BuildTask {

    override val platform: Platform = Platform.COMMON
    override val isTest: Boolean = fragment.isTest

    private val additionalKotlinJavaSourceDirs by Selectors.fromFragment(
        type = KotlinJavaSourceDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun run(dependenciesResult: List<TaskResult>): Result {
        logger.debug("compile metadata for '${module.userReadableName}' -- ${fragment.name}")

        // TODO Make kotlin version configurable in settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinSettings = listOf(fragment).mergedKotlinSettings()

        val dependencyResolutionResults = dependenciesResult.filterIsInstance<ResolveExternalDependenciesTask.Result>()

        // TODO extract deps only for our fragment/platforms
        val mavenClasspath = dependencyResolutionResults.flatMap { it.compileClasspath }

        val localDependencies = dependenciesResult.filterIsInstance<Result>()

        // includes this module's fragment dependencies and other source fragment deps from other local modules
        val localClasspath = localDependencies.map { it.metadataOutputRoot }

        val classpath = localClasspath + mavenClasspath // TODO where to get transitive maven deps?
        val refinesPaths = fragment.refinedFragments.map { localDependencies.findMetadataResultForFragment(it).metadataOutputRoot }
        val friendPaths = fragment.friends.map { localDependencies.findMetadataResultForFragment(it).metadataOutputRoot }

        // TODO settings
        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val configuration: Map<String, String> = mapOf(
            "jdk.url" to jdk.downloadUrl.toString(),
            "user.settings" to Json.encodeToString(kotlinSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val sourceDirs = fragment.src.toAbsolutePath() + additionalKotlinJavaSourceDirs.map { it.path }
        val inputs = sourceDirs + classpath + refinesPaths + friendPaths

        executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val existingSourceDirs = sourceDirs.filter { it.exists() }
            if (existingSourceDirs.isNotEmpty()) {
                existingSourceDirs.forEach {
                    if (!it.isDirectory()) {
                        userReadableError("Source directory '$it' exists, but it's not a directory, this is currently unsupported")
                    }
                }
                compileSources(
                    jdk = jdk,
                    kotlinVersion = kotlinVersion,
                    kotlinUserSettings = kotlinSettings,
                    sourceDirectories = sourceDirs,
                    additionalSourceRoots = additionalKotlinJavaSourceDirs.map { SourceRoot(it.fragmentName, it.path) },
                    classpath = classpath,
                    friendPaths = friendPaths,
                    refinesPaths = refinesPaths,
                )
            } else {
                logger.info("No sources were found for ${fragment.identificationPhrase()}, skipping compilation")
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path.toAbsolutePath()))
        }

        return Result(
            metadataOutputRoot = taskOutputRoot.path.toAbsolutePath(),
            module = module,
            fragment = fragment,
        )
    }

    private fun List<Result>.findMetadataResultForFragment(f: Fragment) =
        // can't use identity check because some fragments are wrapped, and equals is not overridden
        firstOrNull { it.module.userReadableName == f.module.userReadableName && it.fragment.name == f.name }
            ?: error("Metadata compilation result not found for dependency fragment ${f.module.userReadableName}:" +
                    "${f.name} of this fragment ${module.userReadableName}:${fragment.name}. Actual results: " +
                    map { "${it.module.userReadableName}:${it.fragment.name}" })

    private suspend fun compileSources(
        jdk: Jdk,
        kotlinVersion: String,
        kotlinUserSettings: KotlinUserSettings,
        sourceDirectories: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        classpath: List<Path>,
        friendPaths: List<Path>,
        refinesPaths: List<Path>,
    ) {
        val kotlinSourceFiles = sourceDirectories.flatMap { it.walk() }.filter { it.extension == "kt" }.toList()
        if (kotlinSourceFiles.isEmpty()) {
            return
        }

        val compilerJars = kotlinArtifactsDownloader.downloadKotlinCompilerEmbeddable(version = kotlinVersion)
        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(
            kotlinVersion = kotlinVersion,
            kotlinUserSettings = kotlinUserSettings,
        )
        val compilerArgs = kotlinMetadataCompilerArgs(
            kotlinUserSettings = kotlinUserSettings,
            moduleName = module.kotlinModuleName(isTest),
            classpath = classpath,
            compilerPlugins = compilerPlugins,
            outputPath = taskOutputRoot.path,
            friendPaths = friendPaths,
            refinesPaths = refinesPaths,
            fragments = listOf(fragment),
            sourceFiles = sourceDirectories,
            additionalSourceRoots = additionalSourceRoots,
        )
        spanBuilder("kotlin-metadata-compilation")
            .setAmperModule(module)
            .setListAttribute("source-dirs", sourceDirectories.map { it.pathString })
            .setAttribute("compiler-version", kotlinVersion)
            .setListAttribute("compiler-args", compilerArgs)
            .use {
                logger.info("Compiling Kotlin metadata for module '${module.userReadableName}'...")
                val result = jdk.runJava(
                    workingDir = Path("."),
                    mainClass = "org.jetbrains.kotlin.cli.metadata.K2MetadataCompiler",
                    classpath = compilerJars,
                    programArgs = compilerArgs,
                    jvmArgs = listOf(),
                    outputListener = LoggingProcessOutputListener(logger),
                    tempRoot = tempRoot,
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin metadata compilation failed (see errors above)")
                }
            }
    }

    class Result(
        val metadataOutputRoot: Path,
        val module: AmperModule,
        val fragment: Fragment,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
