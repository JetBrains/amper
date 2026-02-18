/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase

/**
 * An order-sensitive list of maven phases that are supported by the maven compatibility layer.
 * 
 * **Important!** Names should be equal to the actual maven phases names.
 */
@Suppress("EnumEntryName")
enum class KnownMavenPhase(
    val beforeTask: (PhaseTaskParameters) -> ArtifactTaskBase = ::BeforeMavenPhaseTask,
    val isTest: Boolean = false,
) {
    validate(::InitialMavenPhaseTask),
    initialize,
    `generate-sources`(::GeneratedSourcesMavenPhaseTask),
    `process-sources`,
    `generate-resources`(::AdditionalResourcesAwareMavenPhaseTask),
    `process-resources`,
    compile(::ClassesAwareMavenPhaseTask),
    `process-classes`,
    `generate-test-sources`(::GeneratedSourcesMavenPhaseTask, isTest = true),
    `process-test-sources`(isTest = true),
    `generate-test-resources`(::AdditionalResourcesAwareMavenPhaseTask, isTest = true),
    `process-test-resources`(isTest = true),
    `test-compile`(::ClassesAwareMavenPhaseTask, isTest = true),
    `process-test-classes`(isTest = true),
    test,
    `prepare-package`,
    `package`,
    `pre-integration-test`,
    `integration-test`,
    `post-integration-test`,
    verify,
    install,
    deploy,
    ;

    context(moduleCtx: ModuleSequenceCtx)
    val beforeTaskName get() = TaskName.fromHierarchy(listOf(moduleCtx.module.userReadableName, "maven", name, "before"))

    context(moduleCtx: ModuleSequenceCtx)
    val afterTaskName get() = TaskName.fromHierarchy(listOf(moduleCtx.module.userReadableName, "maven", name, "after"))

    val dependsOn get() = entries.getOrNull(entries.indexOf(this) - 1)

    context(moduleCtx: ModuleSequenceCtx, taskBuilder: ProjectTasksBuilder)
    fun createBeforeTask(sharedMavenProject: MavenProject) = beforeTask(
        PhaseTaskParameters(
            taskName = beforeTaskName,
            module = moduleCtx.module,
            isTest = isTest,
            incrementalCache = taskBuilder.context.incrementalCache,
            cacheRoot = taskBuilder.context.userCacheRoot,
            sharedMavenProject = sharedMavenProject,
            amperBuildRoot = taskBuilder.context.buildOutputRoot.path,
        )
    )

    companion object Index : EnumMap<KnownMavenPhase, String>(KnownMavenPhase::values, KnownMavenPhase::name)
}