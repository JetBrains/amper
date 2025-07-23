/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.compose.PreparedComposeResourcesDirArtifact
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory

/**
 * Provides prepared Compose Resources as java resources to be placed into the classpath.
 *
 * **Output**: [JvmResourcesDirArtifact]
 */
class JvmComposeResourcesTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val buildOutputRoot: AmperBuildOutputRoot,
) : PureArtifactTaskBase(buildOutputRoot) {
    private val preparedResources by Selectors.fromFragment(
        type = PreparedComposeResourcesDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.Single,
    )

    private val outputJvmResources by JvmResourcesDirArtifact(
        buildOutputRoot = buildOutputRoot,
        fragment = fragment,
    )

    override suspend fun run(executionContext: TaskGraphExecutionContext) {
        val outputRoot = outputJvmResources.path / preparedResources.packagingDir

        val dir = preparedResources.path
        if (!dir.isDirectory()) {
            outputRoot.deleteRecursively()
            return
        }

        // TODO: Maybe don't copy the files somehow?
        BuildPrimitives.copy(
            from = dir,
            to = outputRoot.createDirectories(),
        )
    }
}