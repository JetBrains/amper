/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import java.nio.file.Path

data class MavenPhaseResult(
    val fragment: Fragment,

    /**
     * Recognizable changes done to the Maven model 
     * by mojo executions that Amper can pass down to other Amper tasks.
     */
    val modelChanges: List<ModelChange>,
) : TaskResult {
    val additionalTestJvmArgs: List<String>
        get() = modelChanges.lastOrNull()?.additionalTestJvmArgs.orEmpty()
    
    val sourceRoots: List<SourceRoot>
        get() = modelChanges
            .flatMap { if (!fragment.isTest) it.additionalSources else it.additionalTestSources }
            .distinct()
            .map { SourceRoot(fragment.name, it) }
}

/**
 * Project model additions brought up by maven mojo executions.
 */
data class ModelChange(
    val additionalSources: List<Path>,
    val additionalTestSources: List<Path>,
    val additionalTestJvmArgs: List<String>,
) : TaskResult

/**
 * After phase task that aggregates model changes from mojo executions.
 * This task depends on:
 * 1. Previous phase after task.
 * 2. Corresponding before task.
 * 3. All mojo tasks for this phase.
 * 
 * It merges previous phase results with the mojo-generated sources/resources into the final [MavenPhaseResult],
 * so that next Amper tasks/Maven phases will have access both to cumulative [ModelChange]s and [MavenPhaseResult].
 */
class AfterMavenPhaseTask(
    override val taskName: TaskName,
    module: AmperModule,
    isTest: Boolean,
) : ArtifactTaskBase() {

    private val targetFragment = module.leafFragments.singleOrNull {
        it.platform == Platform.JVM && it.isTest == isTest
    } ?: error("No relevant JVM fragment was found. This task should be created only for modules with JVM platform.")

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): MavenPhaseResult {
        // Get the previous phase task result (should be exactly one)
        val previousPhaseResult = dependenciesResult
            .filterIsInstance<MavenPhaseResult>()
            .singleOrNull()

        // Get model changes from mojos
        val mojoExecutionsModelChanges = dependenciesResult
            .filterIsInstance<ModelChange>()

        return MavenPhaseResult(
            fragment = targetFragment,
            modelChanges = previousPhaseResult?.modelChanges.orEmpty() + mojoExecutionsModelChanges,
        )
    }
}