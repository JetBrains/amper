/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.intellij.openapi.project.Project
import org.jetbrains.amper.frontend.PotatoModule
import java.io.File


/**
 * Class to aggregate file naming conventions.
 */
class FileConventions(
    val intellijProject: Project,
    module: PotatoModule,
    taskDir: File,
) {
    val baseDir: File = taskDir.resolve("build")
    val projectDir: File = baseDir.resolve("${module.userReadableName}.xcodeproj")
    val intermediatesDir: File = taskDir.resolve("tmp")
    val frameworksStagingDir = intermediatesDir.resolve("frameworks")
    val symRoot = taskDir.resolve("bin")

    val derivedDataPathString = taskDir.resolve("derivedData").relativeToBase().path
    val objRootPathString = intermediatesDir.relativeToBase().path
    val symRootPathString = symRoot.relativeToBase().path
    val frameworksStagingPathString = frameworksStagingDir.relativeToBase().path

    fun File.relativeToBase() = relativeTo(baseDir).normalize()

    init {
        baseDir.mkdirs()
        projectDir.mkdirs()
    }
}