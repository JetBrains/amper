/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.schema.ComposeResourcesSettings
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.refinedLeafFragmentsDependingOn

fun ProjectTasksBuilder.setupComposeTasks() {
    configureComposeResourcesGeneration()
}

private fun ProjectTasksBuilder.configureComposeResourcesGeneration() {
    allModules().withEach configureModule@ {
        if (!isComposeEnabledFor(module)) {
            return@configureModule
        }

        val rootFragment = module.rootFragment
        val config = rootFragment.settings.compose.resources
        val packageName = config.getResourcesPackageName(module)
        val makeAccessorsPublic = config.exposedAccessors
        val packagingDir = "composeResources/$packageName/"

        // `expect` is generated in `common` only, while `actual` are generated in the refined fragments.
        //  do not separate `expect`/`actual` if the module only contains a single fragment.
        val shouldSeparateExpectActual = module.fragments.size > 1

        /*
         The tasks generate code (collectors and Res) if either is true:
          - The project has some actual resources in any of the fragments.
          - The user explicitly requested to make the resources API public.
            We generate public code to make API not depend on the actual presence of the resources,
            because the user already opted-in to their usage.
        */
        val shouldGenerateCode = makeAccessorsPublic || rootFragment.module.fragments.any { it.hasAnyComposeResources }

        // Configure "global" tasks that generate common code (into rootFragment).
        tasks.registerTask(
            GenerateResClassTask(
                buildOutputRoot = context.buildOutputRoot,
                rootFragment = rootFragment,
                makeAccessorsPublic = makeAccessorsPublic,
                packageName = packageName,
                packagingDir = packagingDir,
                shouldGenerateCode = shouldGenerateCode,
            )
        )
        if (shouldSeparateExpectActual) {
            tasks.registerTask(
                GenerateExpectResourceCollectorsTask(
                    buildOutputRoot = context.buildOutputRoot,
                    rootFragment = rootFragment,
                    packageName = packageName,
                    makeAccessorsPublic = makeAccessorsPublic,
                    shouldGenerateCode = shouldGenerateCode,
                )
            )
        }

        // Configure per-fragment tasks, as resources may reside in arbitrary fragments.
        module.fragments.forEach { fragment ->
            tasks.registerBuiltinArtifact(
                ComposeResourcesSourceDirArtifact(
                    buildOutputRoot = context.buildOutputRoot,
                    fragment = fragment,
                    conventionPath = fragment.composeResourcesPath,
                )
            )
            tasks.registerTask(
                PrepareComposeResourcesTask(
                    buildOutputRoot = context.buildOutputRoot,
                    fragment = fragment,
                    packagingDir = packagingDir,
                )
            )
            tasks.registerTask(
                GenerateResourceAccessorsTask(
                    buildOutputRoot = context.buildOutputRoot,
                    fragment = fragment,
                    packageName = packageName,
                    packagingDir = packagingDir,
                    makeAccessorsPublic = makeAccessorsPublic,
            ))
        }

        // Configure tasks that generate code into the leaf-fragments
        refinedLeafFragmentsDependingOn(rootFragment).forEach { fragment ->
            tasks.registerTask(
                GenerateActualResourceCollectorsTask(
                    buildOutputRoot = context.buildOutputRoot,
                    useActualModifier = shouldSeparateExpectActual,
                    fragment = fragment,
                    packageName = packageName,
                    makeAccessorsPublic = makeAccessorsPublic,
                    shouldGenerateCode = shouldGenerateCode,
                )
            )
            tasks.registerTask(
                MergePreparedComposeResourcesTask(
                    buildOutputRoot = context.buildOutputRoot,
                    fragment = fragment,
                    packagingDir = packagingDir,
                )
            )
        }
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
