/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
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

        if (!isComposeResourcesEnabledFor(module)) {
            return@configureModule
        }

        val codegenTasksByLeafFragment = mutableMapOf<Fragment, MutableList<TaskName>>()
        fun addCodegenTaskForRegistering(fragment: Fragment, codeGenTaskName: TaskName) {
            refinedLeafFragmentsDependingOn(fragment).forEach { leafFragment ->
                codegenTasksByLeafFragment.getOrPut(leafFragment, ::mutableListOf).add(codeGenTaskName)
            }
        }

        val rootFragment = checkNotNull(module.rootFragment) { "Root fragment expected" }
        val config = rootFragment.settings.compose.resources
        val packageName = config.getResourcesPackageName(module)
        val makeAccessorsPublic = config.exposedAccessors
        val packagingDir = "composeResources/$packageName/"

        // `expect` is generated in `common` only, while `actual` are generated in the refined fragments.
        //  do not separate `expect`/`actual` if the module only contains a single fragment.
        val shouldSeparateExpectActual = rootFragment.platforms.size > 1

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
        if (shouldSeparateExpectActual) {
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
                        makeAccessorsPublic = makeAccessorsPublic,
                        buildOutputRoot = context.buildOutputRoot,
                        useActualModifier = shouldSeparateExpectActual,
                    ),
                    // FIXME: Maybe a bug here, if fragmentDependencies are not transitive
                    dependsOn = fragment.fragmentDependencies
                        .filter { it.type == FragmentDependencyType.REFINE }
                        .map { ComposeFragmentTaskType.ComposeResourcesGenerateAccessors.getTaskName(it.target) },
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
        val packageParts = module.rootFragment?.inferPackageNameFromPublishing() ?: module.inferPackageNameFromModule()
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

private fun PotatoModule.inferPackageNameFromModule(): List<String> {
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

internal enum class CommonGlobalTaskType(
    private val keywordName: String,
) {
    // compose resources
    ComposeResourcesGenerateResClass("generateComposeResClass"),
    ComposeResourcesGenerateExpect("generateExpectComposeResourceCollectors"),
    ;

    fun getTaskName(module: PotatoModule) = TaskName.moduleTask(module, keywordName)
}