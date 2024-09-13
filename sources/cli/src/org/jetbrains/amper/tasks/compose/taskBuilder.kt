/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.schema.ComposeResourcesSettings
import org.jetbrains.amper.frontend.schema.commonSettings
import org.jetbrains.amper.maven.publicationCoordinates
import org.jetbrains.amper.tasks.CommonFragmentTaskType
import org.jetbrains.amper.tasks.CommonGlobalTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.compilationTaskNamesFor
import org.jetbrains.amper.tasks.refinedLeafFragmentsDependingOn

fun ProjectTasksBuilder.setupComposeTasks() {
    configureComposeResourcesGeneration()
}

private fun ProjectTasksBuilder.configureComposeResourcesGeneration() {
    allModules().withEach configureModule@ {
        val commonComposeSettings = module.origin.commonSettings.compose
        if (!commonComposeSettings.enabled) {
            return@configureModule
        }

        val rootFragment = checkNotNull(module.rootFragment) { "Root fragment expected" }

        if (!commonComposeSettings.resources.enabled) {
            return@configureModule
        }

        val codegenTasksByLeafFragment = mutableMapOf<Fragment, MutableList<TaskName>>()
        fun addCodegenTaskForRegistering(fragment: Fragment, codeGenTaskName: TaskName) {
            refinedLeafFragmentsDependingOn(fragment).forEach { leafFragment ->
                codegenTasksByLeafFragment.getOrPut(leafFragment, ::mutableListOf).add(codeGenTaskName)
            }
        }

        val config = rootFragment.settings.compose.resources
        val packageName = config.getResourcesPackageName(module)
        val makeAccessorsPublic = config.exposedAccessors
        val packagingDir = "composeResources/$packageName"

        // Configure "global" tasks that generate common code (into rootFragment).
        CommonGlobalTaskType.ComposeResourcesGenerateResClass.getTaskName(module).let { taskName ->
            tasks.registerTask(
                task = GenerateResClassTask(
                    taskName = taskName,
                    fragment = rootFragment,
                    packageName = packageName,
                    makeAccessorsPublic = makeAccessorsPublic,
                    packagingDir = packagingDir,
                    buildOutputRoot = context.buildOutputRoot,
                ),
            )
            addCodegenTaskForRegistering(rootFragment, taskName)
        }
        CommonGlobalTaskType.ComposeResourcesGenerateExpect.getTaskName(module).let { taskName ->
            tasks.registerTask(
                task = GenerateExpectResourceCollectorsTask(
                    taskName = taskName,
                    fragment = rootFragment,
                    packageName = packageName,
                    makeAccessorsPublic = makeAccessorsPublic,
                    buildOutputRoot = context.buildOutputRoot,
                ),
            )
            addCodegenTaskForRegistering(rootFragment, taskName)
        }

        // Configure per-fragment tasks, as resources may reside in arbitrary fragments.
        module.fragments.forEach { fragment ->
            val prepareResourcesTaskName = CommonFragmentTaskType.ComposeResourcesPrepare.getTaskName(fragment)
            tasks.registerTask(
                task = PrepareComposeResourcesTask(
                    taskName = prepareResourcesTaskName,
                    fragment = fragment,
                    originalResourcesDir = fragment.composeResourcesPath,
                    taskOutputRoot = context.getTaskOutputPath(prepareResourcesTaskName),
                )
            )

            CommonFragmentTaskType.ComposeResourcesGenerateAccessors.getTaskName(fragment).let { taskName ->
                tasks.registerTask(
                    task = GenerateResourceAccessorsTask(
                        taskName = taskName,
                        packageName = packageName,
                        fragment = fragment,
                        makeAccessorsPublic = makeAccessorsPublic,
                        packagingDir = packagingDir,
                        buildOutputRoot = context.buildOutputRoot,
                    ),
                    dependsOn = listOf(
                        prepareResourcesTaskName,
                    ),
                )
                addCodegenTaskForRegistering(fragment, taskName)
            }
        }

        // Configure tasks that generate code into the leaf-fragments
        refinedLeafFragmentsDependingOn(rootFragment).forEach { fragment ->
            CommonFragmentTaskType.ComposeResourcesGenerateActual.getTaskName(fragment).let { taskName ->
                tasks.registerTask(
                    task = GenerateActualResourceCollectorsTask(
                        taskName = taskName,
                        fragment = fragment,
                        packageName = packageName,
                        makeAccessorsPublic = makeAccessorsPublic,
                        buildOutputRoot = context.buildOutputRoot,
                    ),
                    // FIXME: Maybe a bug here, if fragmentDependencies are not transitive
                    dependsOn = fragment.fragmentDependencies
                        .filter { it.type == FragmentDependencyType.REFINE }
                        .map { CommonFragmentTaskType.ComposeResourcesGenerateAccessors.getTaskName(it.target) },
                )
                addCodegenTaskForRegistering(fragment, taskName)
            }
        }

        // Register compilation tasks dependencies on codegen tasks
        for ((leafFragment, codegenTasks) in codegenTasksByLeafFragment) {
            for (compileTaskName in compilationTaskNamesFor(leafFragment)) {
                for (codegenTaskName in codegenTasks) {
                    tasks.registerDependency(
                        taskName = compileTaskName,
                        dependsOn = codegenTaskName,
                    )
                }
            }
        }
        // TODO: Wire metadata tasks correctly too (currently broken anyway)
    }
}

private fun ComposeResourcesSettings.getResourcesPackageName(module: PotatoModule): String {
    return packageName.takeIf { it.isNotEmpty() } ?: run {
        // FIXME: no publication may be available, so it will crash.
        //  unlike Gradle, Amper doesn't have default group/name prepopulated. Maybe use module name?
        val coordinates = module.publicationCoordinates(Platform.COMMON)
        val groupName = coordinates.groupId.lowercase().asUnderscoredIdentifier()
        val moduleName = coordinates.artifactId.lowercase().asUnderscoredIdentifier()
        val id = if (groupName.isNotEmpty()) "$groupName.$moduleName" else moduleName
        "$id.generated.resources"
    }
}

private fun String.asUnderscoredIdentifier(): String =
    replace('-', '_')
        .let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }