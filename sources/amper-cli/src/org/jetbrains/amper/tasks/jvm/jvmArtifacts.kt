/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.artifacts.PlatformScopedArtifact

/**
 * An artifact that is associated with a directory where compiled jvm classes are located.
 */
class CompiledJvmClassesArtifact(
    buildOutputRoot: AmperBuildOutputRoot,
    module: AmperModule,
    platform: Platform,
    isTest: Boolean,
) : PlatformScopedArtifact(buildOutputRoot, module, platform, isTest)