/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.frontend.schema.KotlinVersion
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString
import kotlin.io.path.writeText

internal data class KotlinUserSettings(
    val languageVersion: KotlinVersion,
    val apiVersion: KotlinVersion,
    val allWarningsAsErrors: Boolean,
    val suppressWarnings: Boolean,
    val verbose: Boolean,
    val progressiveMode: Boolean,
    val languageFeatures: List<String>,
    val optIns: List<String>,
    val freeCompilerArgs: List<String>,
    val serialization: String?,
)

internal fun kotlinCompilerArgs(
    isMultiplatform: Boolean,
    kotlinUserSettings: KotlinUserSettings,
    classpath: List<Path>,
    compilerPlugins: List<Path>,
    jdkHome: Path,
    outputPath: Path
): List<String> = buildList {
    if (isMultiplatform) {
        add("-Xmulti-platform")
    }
    add("-jvm-target")
    add("17")

    add("-jdk-home")
    add(jdkHome.pathString)

    add("-classpath")
    add(classpath.joinToString(File.pathSeparator))

    add("-no-stdlib")

    add("-language-version")
    add(kotlinUserSettings.languageVersion.schemaValue)

    add("-api-version")
    add(kotlinUserSettings.apiVersion.schemaValue)

    if (kotlinUserSettings.allWarningsAsErrors) {
        add("-Werror")
    }
    if (kotlinUserSettings.suppressWarnings) {
        add("-nowarn")
    }
    if (kotlinUserSettings.verbose) {
        add("-verbose")
    }
    if (kotlinUserSettings.progressiveMode) {
        add("-progressive")
    }
    kotlinUserSettings.optIns.forEach {
        add("-opt-in")
        add(it)
    }
    kotlinUserSettings.languageFeatures.forEach {
        add("-XXLanguage:+$it")
    }
    kotlinUserSettings.freeCompilerArgs.forEach {
        add(it)
    }
    compilerPlugins.forEach {
        add("-Xplugin=$it")
    }

    // -d is after freeCompilerArgs because we don't allow overriding the output dir (it breaks task dependencies)
    // (specifying -d multiple times generates a warning, and only the last value is used)
    // TODO forbid -d in freeCompilerArgs in the frontend, so it's clearer for the users
    add("-d")
    add(outputPath.pathString)
}

inline fun <R> withKotlinCompilerArgFile(args: List<String>, tempRoot: AmperProjectTempRoot, block: (Path) -> R): R {
    // escaping rules from https://github.com/JetBrains/kotlin/blob/6161f44d91e235750077e1aaa5faff7047316190/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/preprocessCommandLineArguments.kt#L83
    val argString = args.joinToString(" ") { arg ->
        if (arg.contains(" ") || arg.contains("'")) {
            "'${arg.replace("\\", "\\\\").replace("'", "\\'")}'"
        } else {
            arg
        }
    }

    tempRoot.path.createDirectories()
    val argFile = Files.createTempFile(tempRoot.path, "kotlin-args-", ".txt")
    return try {
        argFile.writeText(argString)
        block(argFile)
    } finally {
        argFile.deleteExisting()
    }
}
