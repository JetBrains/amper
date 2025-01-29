/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedCollectorsPath
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
import org.jetbrains.compose.resources.generateExpectResourceCollectors
import kotlin.io.path.createDirectory

/**
 * See [generateExpectResourceCollectors] dir.
 */
class GenerateExpectResourceCollectorsTask(
    rootFragment: Fragment,
    buildOutputRoot: AmperBuildOutputRoot,
    private val packageName: String,
    private val makeAccessorsPublic: Boolean,
    private val shouldGenerateCode: Boolean,
) : PureArtifactTaskBase(buildOutputRoot) {
    private val codeDir by KotlinJavaSourceDirArtifact(
        buildOutputRoot,
        rootFragment,
        conventionPath = rootFragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot.path),
    )

    override suspend fun run() {
        if (shouldGenerateCode) {
            generateExpectResourceCollectors(
                packageName = packageName,
                makeAccessorsPublic = makeAccessorsPublic,
                outputSourceDirectory = codeDir.path.createDirectory(),
            )
        }
    }
}