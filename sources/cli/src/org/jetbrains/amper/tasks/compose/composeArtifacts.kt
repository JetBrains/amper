/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.artifacts.FragmentScopedArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.PlatformScopedArtifact
import java.nio.file.Path

/**
 * `composeResources/` user source directory.
 */
open class ComposeResourcesSourceDirArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: Fragment,
    @Transient override val conventionPath: Path? = null,
) : FragmentScopedArtifact(buildOutputRoot, fragment)

/**
 * Processed compose resources.
 */
class PreparedComposeResourcesDirArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: Fragment,
    /**
     * The relative path where the runtime library expects to find the resources.
     */
    val packagingDir: String,
) : FragmentScopedArtifact(buildOutputRoot, fragment)

class ComposeResourcesAccessorsDirArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: Fragment,
    conventionPath: Path? = null,
) : KotlinJavaSourceDirArtifact(buildOutputRoot, fragment, conventionPath)

/**
 * Merged [PreparedComposeResourcesDirArtifact] resources,
 * already placed under [PreparedComposeResourcesDirArtifact.packagingDir] internally.
 *
 * Use to depend on compose resources from other modules.
 */
class MergedPreparedComposeResourcesDirArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    module: AmperModule,
    platform: Platform,
) : PlatformScopedArtifact(buildOutputRoot, module, platform)
