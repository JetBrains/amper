/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.allRefinedFragmentDependencies
import org.jetbrains.amper.frontend.schema.ComposeResourcesSettings
import org.jetbrains.amper.tasks.FragmentTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.compilationTaskNamesFor
import org.jetbrains.amper.tasks.refinedLeafFragmentsDependingOn

fun ProjectTasksBuilder.setupComposeTasks() {
    configureComposeResourcesGeneration()
}

private fun ProjectTasksBuilder.configureComposeResourcesGeneration() {
    allModules().withEach configureModule@ {
        if (!isComposeEnabledFor(module)) {
            return@configureModule
        }

        val codegenTasksByLeafFragment = mutableMapOf<Fragment, MutableList<TaskName>>()
        fun addCodegenTaskForRegistering(fragment: Fragment, codeGenTaskName: TaskName) {
            refinedLeafFragmentsDependingOn(fragment).forEach { leafFragment ->
                codegenTasksByLeafFragment.getOrPut(leafFragment, ::mutableListOf).add(codeGenTaskName)
            }
        }

        val rootFragment = module.rootFragment
        val config = rootFragment.settings.compose.resources
        val packageName = config.getResourcesPackageName(module)
        val makeAccessorsPublic = config.exposedAccessors
        val packagingDir = "composeResources/$packageName/"

        // `expect` is generated in `common` only, while `actual` are generated in the refined fragments.
        //  do not separate `expect`/`actual` if the module only contains a single fragment.
        val shouldSeparateExpectActual = rootFragment.platforms.size > 1

        val shouldGenerateCode = {
            /*
              The tasks generate code (collectors and Res) if either is true:
               - The project has some actual resources in any of the fragments.
               - The user explicitly requested to make the resources API public.
                 We generate public code to make API not depend on the actual presence of the resources,
                 because the user already opted-in to their usage.
            */
            makeAccessorsPublic || rootFragment.module.fragments.any { it.hasAnyComposeResources }
        }

        // Configure "global" tasks that generate common code (into rootFragment).
        ComposeGlobalTaskType.ComposeResourcesGenerateResClass.getTaskName(module).let { taskName ->
            tasks.registerTask(
                task = GenerateResClassTask(
                    taskName = taskName,
                    shouldGenerateCode = shouldGenerateCode,
                    rootFragment = rootFragment,
                    packageName = packageName,
                    makeAccessorsPublic = makeAccessorsPublic,
                    packagingDir = packagingDir,
                    buildOutputRoot = context.buildOutputRoot,
                    executeOnChangedInputs = executeOnChangedInputs,
                ),
            )
            addCodegenTaskForRegistering(rootFragment, taskName)
        }
        if (shouldSeparateExpectActual) {
            ComposeGlobalTaskType.ComposeResourcesGenerateExpect.getTaskName(module).let { taskName ->
                tasks.registerTask(
                    task = GenerateExpectResourceCollectorsTask(
                        taskName = taskName,
                        shouldGenerateCode = shouldGenerateCode,
                        fragment = rootFragment,
                        packageName = packageName,
                        makeAccessorsPublic = makeAccessorsPublic,
                        buildOutputRoot = context.buildOutputRoot,
                        executeOnChangedInputs = executeOnChangedInputs,
                    ),
                )
                addCodegenTaskForRegistering(rootFragment, taskName)
            }
        }

        // Configure per-fragment tasks, as resources may reside in arbitrary fragments.
        module.fragments.forEach { fragment ->
            val prepareResourcesTaskName = ComposeFragmentTaskType.ComposeResourcesPrepare.getTaskName(fragment)
            tasks.registerTask(
                task = PrepareComposeResourcesTask(
                    taskName = prepareResourcesTaskName,
                    fragment = fragment,
                    originalResourcesDir = fragment.composeResourcesPath,
                    packagingDir = packagingDir,
                    taskOutputRoot = context.getTaskOutputPath(prepareResourcesTaskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                )
            )

            ComposeFragmentTaskType.ComposeResourcesGenerateAccessors.getTaskName(fragment).let { taskName ->
                tasks.registerTask(
                    task = GenerateResourceAccessorsTask(
                        taskName = taskName,
                        packageName = packageName,
                        fragment = fragment,
                        makeAccessorsPublic = makeAccessorsPublic,
                        buildOutputRoot = context.buildOutputRoot,
                        executeOnChangedInputs = executeOnChangedInputs,
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
            ComposeFragmentTaskType.ComposeResourcesGenerateActual.getTaskName(fragment).let { taskName ->
                tasks.registerTask(
                    task = GenerateActualResourceCollectorsTask(
                        taskName = taskName,
                        fragment = fragment,
                        packageName = packageName,
                        shouldGenerateCode = shouldGenerateCode,
                        makeAccessorsPublic = makeAccessorsPublic,
                        buildOutputRoot = context.buildOutputRoot,
                        useActualModifier = shouldSeparateExpectActual,
                        executeOnChangedInputs = executeOnChangedInputs,
                    ),
                    dependsOn = fragment.allRefinedFragmentDependencies()
                        .map { ComposeFragmentTaskType.ComposeResourcesGenerateAccessors.getTaskName(it) }
                        .toList(),
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

private fun ComposeResourcesSettings.getResourcesPackageName(module: AmperModule): String {
    return packageName.takeIf { it.isNotEmpty() } ?: run {
        val packageParts = module.rootFragment.inferPackageNameFromPublishing() ?: module.inferPackageNameFromModule()
        (packageParts + listOf("generated", "resources")).joinToString(separator = ".") {
            it.lowercase().asUnderscoredIdentifier()
        }
    }
}

private fun Fragment.inferPackageNameFromPublishing(): List<String>? {
    return settings.publishing?.let {
        listOfNotNull(it.group, it.name).takeIf(List<*>::isNotEmpty)
    }
}

private fun AmperModule.inferPackageNameFromModule(): List<String> {
    return listOf(userReadableName)
}

private fun String.asUnderscoredIdentifier(): String =
    replace('-', '_')
        .let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }

internal enum class ComposeFragmentTaskType(override val prefix: String) : FragmentTaskType {
    ComposeResourcesPrepare("prepareComposeResourcesFor"),
    ComposeResourcesGenerateAccessors("generateComposeResourceAccessorsFor"),
    ComposeResourcesGenerateActual("generateActualComposeResourceCollectorsFor"),
}

internal enum class ComposeGlobalTaskType(
    private val keywordName: String,
) {
    // compose resources
    ComposeResourcesGenerateResClass("generateComposeResClass"),
    ComposeResourcesGenerateExpect("generateExpectComposeResourceCollectors"),
    ;

    fun getTaskName(module: AmperModule) = TaskName.moduleTask(module, keywordName)
}