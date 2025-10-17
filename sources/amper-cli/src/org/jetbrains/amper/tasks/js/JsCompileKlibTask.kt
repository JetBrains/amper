/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.js

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.ResolvedCompilerPlugin
import org.jetbrains.amper.compilation.kotlinJsCompilerArgs
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.web.WebCompileKlibTask
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path

internal class JsCompileKlibTask(
    module: AmperModule,
    platform: Platform,
    userCacheRoot: AmperUserCacheRoot,
    taskOutputRoot: TaskOutputRoot,
    incrementalCache: IncrementalCache,
    taskName: TaskName,
    tempRoot: AmperProjectTempRoot,
    isTest: Boolean,
    jdkProvider: JdkProvider,
    buildType: BuildType? = null,
    kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, incrementalCache),
) : WebCompileKlibTask(
    module,
    platform,
    userCacheRoot,
    jdkProvider,
    taskOutputRoot,
    incrementalCache,
    taskName,
    tempRoot,
    isTest,
    buildType,
    kotlinArtifactsDownloader,
) {
    override val expectedPlatform: Platform
        get() = Platform.JS

    override fun kotlinCompilerArgs(
        kotlinUserSettings: KotlinUserSettings,
        compilerPlugins: List<ResolvedCompilerPlugin>,
        libraryPaths: List<Path>,
        outputPath: Path,
        friendPaths: List<Path>,
        fragments: List<Fragment>,
        sourceFiles: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        moduleName: String,
        compilationType: KotlinCompilationType,
        include: Path?,
    ): List<String> = kotlinJsCompilerArgs(
        kotlinUserSettings,
        compilerPlugins,
        libraryPaths,
        outputPath,
        friendPaths,
        fragments,
        sourceFiles,
        additionalSourceRoots,
        moduleName,
        compilationType,
        include,
    )
}
