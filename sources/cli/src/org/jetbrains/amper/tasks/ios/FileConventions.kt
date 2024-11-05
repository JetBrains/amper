/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.frontend.AmperModule
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo


/**
 * Class to aggregate file naming conventions.
 */
class FileConventions(
    module: AmperModule,
    taskDir: Path,
) {
    val baseDir: Path = taskDir.resolve("build")
    val projectDir: Path = baseDir.resolve("${module.userReadableName}.xcodeproj")
    val intermediatesDir: Path = taskDir.resolve("tmp")
    val symRoot: Path = taskDir.resolve("bin")

    val derivedDataPathString = taskDir.resolve("derivedData").relativeToBase().pathString
    val objRootPathString = intermediatesDir.relativeToBase().pathString
    val symRootPathString = symRoot.relativeToBase().pathString

    fun Path.relativeToBase(): Path = relativeTo(baseDir).normalize()

    init {
        baseDir.createDirectories()
        projectDir.createDirectories()
    }
}