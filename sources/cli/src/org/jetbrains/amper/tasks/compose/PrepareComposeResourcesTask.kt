/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.compose.resources.prepareResources
import kotlin.io.path.createDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.walk

/**
 * See [prepareResources] step.
 */
class PrepareComposeResourcesTask(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: Fragment,
    packagingDir: String,
) : PureArtifactTaskBase(buildOutputRoot) {
    private val sourceDirs by Selectors.fromFragment(
        type = ComposeResourcesSourceDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.Any,
    )

    private val prepared by PreparedComposeResourcesDirArtifact(
        buildOutputRoot = buildOutputRoot,
        fragment = fragment,
        packagingDir = packagingDir,
    )

    override suspend fun run() {
        if (!sourceDirs.all { sourceDir -> sourceDir.path.walk().any { !it.isHidden() } }) {
            return
        }

        for (sourceDirArtifact in sourceDirs) {
            prepareResources(
                qualifier = prepared.fragmentName,
                originalResourcesDir = sourceDirArtifact.path,
                outputDirectory = prepared.path.createDirectory(),
            )
        }
    }
}
