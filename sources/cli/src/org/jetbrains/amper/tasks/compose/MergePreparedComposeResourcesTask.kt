/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory

class MergePreparedComposeResourcesTask(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: LeafFragment,
    packagingDir: String,
) : PureArtifactTaskBase(buildOutputRoot) {
    private val packagingDir by extraInput(packagingDir)

    private val preparedDirs by Selectors.fromFragmentWithDependencies(
        type = PreparedComposeResourcesDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.Any,
    )

    private val mergedPreparedDir by MergedPreparedComposeResourcesDirArtifact(
        buildOutputRoot, module = fragment.module, platform = fragment.platform,
    )

    override suspend fun run() {
        val existingPreparedDirs = preparedDirs.filter { it.path.isDirectory() }
        if (existingPreparedDirs.isNotEmpty()) {
            val outputPath = mergedPreparedDir.path / packagingDir
            for (preparedDir in existingPreparedDirs) {
                BuildPrimitives.copy(
                    from = preparedDir.path,
                    to = outputPath.createDirectories(),
                )
            }
        }
    }
}