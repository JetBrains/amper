/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.serializableKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * A task that runs the Kotlin/Native cinterop tool.
 */
internal class CinteropTask(
    override val module: AmperModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val incrementalCache: IncrementalCache,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    override val buildType: BuildType,
    private val defFile: Path,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, incrementalCache),
) : BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        // For now, we assume a single fragment. This might need to be adjusted.
        val fragment = module.fragments.first { it.platforms.contains(platform) && it.isTest == isTest }
        val kotlinUserSettings = fragment.serializableKotlinSettings()

        val configuration = mapOf(
            "kotlin.version" to kotlinUserSettings.compilerVersion,
            "def.file" to defFile.toString(),
        )
        val inputs = listOf(defFile)

        val artifact = incrementalCache.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val outputKLib = taskOutputRoot.path.resolve(defFile.toFile().nameWithoutExtension + ".klib")

            val nativeCompiler = downloadNativeCompiler(kotlinUserSettings.compilerVersion, userCacheRoot)
            val args = listOf(
                "-def", defFile.toString(),
                "-o", outputKLib.toString(),
                "-compiler-option", "-I.",
                "-target", platform.nameForCompiler,
            )

            logger.info("Running cinterop for '${defFile.fileName}'...")
            nativeCompiler.cinterop(args, module)

            return@execute IncrementalCache.ExecutionResult(listOf(outputKLib))
        }.outputs.singleOrNull()

        return Result(
            compiledKlib = artifact,
            taskName = taskName,
        )
    }

    class Result(
        val compiledKlib: Path?,
        val taskName: TaskName,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}