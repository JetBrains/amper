/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.CompilerPlugin
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.kotlinWasmJsCompilerArgs
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.web.WebCompileKlibTask
import java.nio.file.Path

class WasmJsCompileKlibTask(
    module: AmperModule,
    platform: Platform,
    userCacheRoot: AmperUserCacheRoot,
    taskOutputRoot: TaskOutputRoot,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    taskName: TaskName,
    tempRoot: AmperProjectTempRoot,
    isTest: Boolean,
    kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
) : WebCompileKlibTask(
    module,
    platform,
    userCacheRoot,
    taskOutputRoot,
    executeOnChangedInputs,
    taskName,
    tempRoot,
    isTest,
    kotlinArtifactsDownloader,
) {
    override val expectedPlatform: Platform
        get() = Platform.WASM_JS

    override fun kotlinCompilerArgs(
        kotlinUserSettings: KotlinUserSettings,
        compilerPlugins: List<CompilerPlugin>,
        libraryPaths: List<Path>,
        outputPath: Path,
        friendPaths: List<Path>,
        fragments: List<Fragment>,
        sourceFiles: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        moduleName: String,
        compilationType: KotlinCompilationType,
        include: Path?
    ): List<String> =
        kotlinWasmJsCompilerArgs(
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
