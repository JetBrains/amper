/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.compose.PrepareComposeResourcesResult
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div

/**
 * Assembles all the required resources into the [IosConventions.getComposeResourcesDirectory]
 * to be later packaged into the iOS app.
 */
class IosComposeResourcesTask(
    override val taskName: TaskName,
    private val leafFragment: LeafFragment,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val outputPath = IosConventions(
            buildRootPath = buildOutputRoot.path,
            moduleName = leafFragment.module.userReadableName,
            buildType = BuildType.Debug,
            platform = leafFragment.platform,
        ).getComposeResourcesDirectory()

        val results = dependenciesResult.filterIsInstance<PrepareComposeResourcesResult.Prepared>()
        if (results.isEmpty()) {
            outputPath.deleteRecursively()
            return EmptyTaskResult
        }

        val config = mapOf(
            "paths" to Json.encodeToString(results.map { it.relativePackagingPath }),
        )

        executeOnChangedInputs.execute(
            id = taskName.name,
            configuration = config,
            inputs = results.map { it.outputDir },
        ) {
            cleanDirectory(outputPath)
            results.forEach { result ->
                val targetDir = outputPath / result.relativePackagingPath
                BuildPrimitives.copy(
                    from = result.outputDir,
                    to = targetDir.createDirectories(),
                )
            }
            ExecuteOnChangedInputs.ExecutionResult(listOf(outputPath))
        }

        return EmptyTaskResult
    }
}