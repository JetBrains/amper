/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.artifacts.api.Artifact
import java.io.Serializable
import java.nio.file.Path
import kotlin.io.path.div

/**
 * A base class for all artifact implementations.
 * Serializable is an implementation detail for the cacheability mechanism.
 */
abstract class ArtifactBase(
    buildOutputRoot: AmperBuildOutputRoot,
) : Artifact, Serializable {
    /**
     * Components that uniquely identify the artifact among the others of the same type.
     * These are used to automatically generate the artifact's path.
     */
    protected abstract fun idComponents() : List<String>

    /**
     * An optional explicitly specified path that should be used instead of auto-generated one.
     * If this is specified, [idComponents] are not used.
     */
    protected open val conventionPath: Path? get() = null

    override val path: Path by lazy {
        conventionPath ?: idComponents()
            .fold(buildOutputRoot.path / "artifacts" / javaClass.simpleName, Path::resolve)
    }
}

/**
 * An artifact is associated with a fragment
 */
abstract class FragmentScopedArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    val fragment: Fragment,
) : ArtifactBase(buildOutputRoot) {
    val module get() = fragment.module
    val fragmentName get() = fragment.name
    val isTest get() = fragment.isTest
    val platforms get() = fragment.platforms

    override fun idComponents() = listOf(module.userReadableName, fragmentName)
}

/**
 * An artifact is associated with a leaf platform and is accessible from the other modules.
 */
abstract class PlatformScopedArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    val module: AmperModule,
    val platform: Platform,
) : ArtifactBase(buildOutputRoot) {
    val moduleName = module.userReadableName

    init {
        require(platform.isLeaf) { "Only leaf platforms are expected here" }
    }

    override fun idComponents() = listOf(module.userReadableName, platform.pretty)
}

/**
 * Kotlin + Java source directory tree.
 */
open class KotlinJavaSourceDirArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: Fragment,
    override val conventionPath: Path? = null,
) : FragmentScopedArtifact(buildOutputRoot, fragment)

/**
 * JVM resources directory tree.
 */
open class JvmResourcesDirArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    fragment: Fragment,
    override val conventionPath: Path? = null,
) : FragmentScopedArtifact(buildOutputRoot, fragment)
