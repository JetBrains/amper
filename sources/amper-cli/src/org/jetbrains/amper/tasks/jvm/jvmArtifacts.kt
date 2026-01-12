/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.artifacts.CompilationScopedArtifact
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.div

/**
 * An artifact that is associated with a directory where compiled jvm classes are located.
 */
class CompiledJvmArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    module: AmperModule,
    platform: Platform,
    isTest: Boolean,
    val buildType: BuildType?,
) : CompilationScopedArtifact(buildOutputRoot, module, platform, isTest) {
    val javaCompilerOutputRoot get() = path / "java-output"
    val kotlinCompilerOutputRoot get() = path / "kotlin-output"
    val resourcesRoot get() = path / "resources-output"
    val jicDataDir get() = path / "jic-data"
    override fun idComponents() = super.idComponents() + listOfNotNull(buildType?.value)
}