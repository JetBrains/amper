/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedCollectorsPath
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.compose.resources.generateActualResourceCollectors
import kotlin.io.path.createDirectory

/**
 * See [generateActualResourceCollectors] step.
 */
class GenerateActualResourceCollectorsTask(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: LeafFragment,
    private val packageName: String,
    private val makeAccessorsPublic: Boolean,
    private val useActualModifier: Boolean,
    private val shouldGenerateCode: Boolean,
) : PureArtifactTaskBase(buildOutputRoot) {
    private val accessorsDirs by Selectors.fromFragment(
        type = ComposeResourcesAccessorsDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.Any,
    )

    private val codeDir by KotlinJavaSourceDirArtifact(
        buildOutputRoot = buildOutputRoot,
        fragment = fragment,
        conventionPath = fragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot.path),
    )

    override suspend fun run() {
        if (shouldGenerateCode) {
            generateActualResourceCollectors(
                packageName = packageName,
                makeAccessorsPublic = makeAccessorsPublic,
                accessorDirectories = accessorsDirs.map { it.path },
                outputSourceDirectory = codeDir.path.createDirectory(),
                useActualModifier = useActualModifier,
            )
        }
    }
}