/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.Jdk
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.kotlinMetadataCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.friends
import org.jetbrains.amper.frontend.refinedFragments
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.walk

class MetadataCompileTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    private val fragment: Fragment,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val terminal: Terminal,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {

    override val platform: Platform = Platform.COMMON
    override val isTest: Boolean = fragment.isTest

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): TaskResult {
        logger.info("compile metadata for '${module.userReadableName}' -- ${fragment.name}")

        // TODO Make kotlin version configurable in settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinSettings = listOf(fragment).mergedKotlinSettings()

        val dependencyResolutionResults = dependenciesResult.filterIsInstance<ResolveExternalDependenciesTask.Result>()

        // TODO extract deps only for our fragment/platforms
        val mavenClasspath = dependencyResolutionResults.flatMap { it.compileClasspath }

        val localDependencies = dependenciesResult.filterIsInstance<TaskResult>()

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

        val sourceDir = fragment.src.toAbsolutePath()
        val inputs = listOf(sourceDir) + classpath + refinesPaths + friendPaths

        executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            if (sourceDir.exists()) {
                if (!sourceDir.isDirectory()) {
                    error("Source directory at '$sourceDir' exists, but it's not a directory, this is currently unsupported")
                }
                compileSources(
                    jdk = jdk,
                    kotlinVersion = kotlinVersion,
                    kotlinUserSettings = kotlinSettings,
                    sourceDirectory = sourceDir,
                    classpath = classpath,
                    friendPaths = friendPaths,
                    refinesPaths = refinesPaths,
                )
            } else {
                logger.info("Sources for fragment '${fragment.name}' of module '${module.userReadableName}' are missing, skipping compilation")
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path.toAbsolutePath()))
        }

        return TaskResult(
            metadataOutputRoot = taskOutputRoot.path.toAbsolutePath(),
            dependencies = dependenciesResult,
            module = module,
            fragment = fragment,
        )
    }

    private fun List<TaskResult>.findMetadataResultForFragment(f: Fragment) =
        // can't use identity check because some fragments are wrapped, and equals is not overridden
        firstOrNull { it.module.userReadableName == f.module.userReadableName && it.fragment.name == f.name }
            ?: error("Metadata compilation result not found for dependency fragment ${f.module.userReadableName}:" +
                    "${f.name} of this fragment ${module.userReadableName}:${fragment.name}. Actual results: " +
                    map { "${it.module.userReadableName}:${it.fragment.name}" })

    @OptIn(ExperimentalPathApi::class)
    private suspend fun compileSources(
        jdk: Jdk,
        kotlinVersion: String,
        kotlinUserSettings: KotlinUserSettings,
        sourceDirectory: Path,
        classpath: List<Path>,
        friendPaths: List<Path>,
        refinesPaths: List<Path>,
    ) {
        val kotlinSourceFiles = sourceDirectory.walk().filter { it.extension == "kt" }.toList()
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
            moduleName = module.userReadableName,
            classpath = classpath,
            compilerPlugins = compilerPlugins,
            outputPath = taskOutputRoot.path,
            friendPaths = friendPaths,
            refinesPaths = refinesPaths,
            fragments = listOf(fragment),
            sourceFiles = listOf(sourceDirectory),
        )
        spanBuilder("kotlin-metadata-compilation")
            .setAmperModule(module)
            .setAttribute("source-dir", sourceDirectory.pathString)
            .setAttribute("compiler-version", kotlinVersion)
            .setListAttribute("compiler-args", compilerArgs)
            .useWithScope {
                logger.info("Calling Kotlin metadata compiler...")
                val result = jdk.runJava(
                    workingDir = Path("."),
                    mainClass = "org.jetbrains.kotlin.cli.metadata.K2MetadataCompiler",
                    classpath = compilerJars,
                    programArgs = compilerArgs,
                    jvmArgs = listOf(),
                    outputListener = LoggingProcessOutputListener(logger),
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin metadata compilation failed (see errors above)")
                }
            }
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val metadataOutputRoot: Path,
        val module: PotatoModule,
        val fragment: Fragment,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
