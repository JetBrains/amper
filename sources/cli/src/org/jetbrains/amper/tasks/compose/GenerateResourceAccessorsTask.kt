/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedAccessorsPath
import org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.compose.resources.generateResourceAccessors
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

/**
 * See [generateResourceAccessors] step.
 */
class GenerateResourceAccessorsTask(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: Fragment,
    private val packageName: String,
    private val packagingDir: String,
    private val makeAccessorsPublic: Boolean,
) : PureArtifactTaskBase(buildOutputRoot) {
    private val prepared by Selectors.fromFragment(
        type = PreparedComposeResourcesDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.Single,
    )
    private val codeDir by ComposeResourcesAccessorsDirArtifact(
        buildOutputRoot = buildOutputRoot,
        fragment = fragment,
        conventionPath = fragment.composeResourcesGeneratedAccessorsPath(buildOutputRoot.path),
    )

    override suspend fun run() {
        if (prepared.path.exists()) {
            generateResourceAccessors(
                packageName = packageName,
                qualifier = prepared.fragmentName,
                makeAccessorsPublic = makeAccessorsPublic,
                packagingDir = packagingDir,
                preparedResourcesDirectory = prepared.path,
                outputSourceDirectory = codeDir.path.createDirectory(),
            )
        }
    }
}
