/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedCommonResClassPath
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
import org.jetbrains.compose.resources.generateResClass
import kotlin.io.path.createDirectory

/**
 * See [generateResClass] step.
 */
class GenerateResClassTask(
    rootFragment: Fragment,
    buildOutputRoot: AmperBuildOutputRoot,
    packageName: String,
    makeAccessorsPublic: Boolean,
    packagingDir: String,
    shouldGenerateCode: Boolean,
) : PureArtifactTaskBase(buildOutputRoot) {
    private val packageName by extraInput(packageName)
    private val makeAccessorsPublic by extraInput(makeAccessorsPublic)
    private val packagingDir by extraInput(packagingDir)
    private val shouldGenerateCode by extraInput(shouldGenerateCode)

    private val codeDir by KotlinJavaSourceDirArtifact(
        buildOutputRoot = buildOutputRoot,
        fragment = rootFragment,
        conventionPath = rootFragment.composeResourcesGeneratedCommonResClassPath(buildOutputRoot.path),
    )

    override suspend fun run() {
        if (shouldGenerateCode) {
            generateResClass(
                packageName = packageName,
                packagingDir = packagingDir,
                isPublic = makeAccessorsPublic,
                outputSourceDirectory = codeDir.path.createDirectory(),
            )
        }
    }
}
