/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.compose.PreparedComposeResourcesDirArtifact
import kotlin.io.path.exists

/**
 * Just passes through the prepared Compose Resources to be packaged as assets for Android.
 *
 * **Output**: [AdditionalAndroidAssetsProvider].
 *
 * @see AndroidAarTask
 */
class AndroidComposeResourcesTask(
    override val taskName: TaskName,
    fragment: Fragment,
) : ArtifactTaskBase() {
    private val preparedResources by Selectors.fromFragment(
        type = PreparedComposeResourcesDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.Single,
    )

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val preparedResourcesPath = preparedResources.path

        return Result(
            assetsRoots = if (preparedResourcesPath.exists()) {
                AdditionalAndroidAssetsProvider.AssetsRoot(
                    path = preparedResourcesPath,
                    relativePackagingPath = preparedResources.packagingDir,
                ).let(::listOf)
            } else emptyList()
        )
    }

    private class Result(
        override val assetsRoots: List<AdditionalAndroidAssetsProvider.AssetsRoot>,
    ) : TaskResult, AdditionalAndroidAssetsProvider
}