/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.tasks.CompileTask
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString
import kotlin.io.path.writeText

private fun kotlinCommonCompilerArgs(
    isMultiplatform: Boolean,
    kotlinUserSettings: KotlinUserSettings,
    compilerPlugins: List<CompilerPlugin>,
): List<String> = buildList {
    if (isMultiplatform) {
        add("-Xmulti-platform")
    }

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
    // Switch to -Xcompiler-plugin option when it's ready (currently a prototype, and K2-only)
    // https://jetbrains.slack.com/archives/C942U8L4R/p1708709995859629
    compilerPlugins.forEach { plugin ->
        add("-Xplugin=${plugin.jarPath}")
        plugin.options.forEach { (optName, value) ->
            add("-P")
            add("plugin:${plugin.id}:$optName=$value")
        }
    }
    kotlinUserSettings.freeCompilerArgs.forEach {
        add(it)
    }
}

internal fun kotlinJvmCompilerArgs(
    isMultiplatform: Boolean,
    userSettings: CompilationUserSettings,
    classpath: List<Path>,
    compilerPlugins: List<CompilerPlugin>,
    jdkHome: Path,
    outputPath: Path,
    friendPaths: List<Path>,
): List<String> = buildList {
    if (userSettings.jvmRelease != null) {
        add("-Xjdk-release=${userSettings.jvmRelease.releaseNumber}")
    }

    add("-jdk-home")
    add(jdkHome.pathString)

    add("-classpath")
    add(classpath.joinToString(File.pathSeparator))

    add("-no-stdlib") // that is specifically for the JVM

    if (friendPaths.isNotEmpty()) {
        // KT-34277 Kotlinc processes -Xfriend-paths differently for Javascript vs. JVM, using different list separators
        // https://github.com/JetBrains/kotlin/blob/4964ee12a994bc846ecdb4810486aaf659be00db/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/K2JVMCompilerArguments.kt#L531
        add("-Xfriend-paths=${friendPaths.joinToString(",")}")
    }

    // Common args last, because they contain free compiler args
    addAll(kotlinCommonCompilerArgs(isMultiplatform, userSettings.kotlin, compilerPlugins))

    // -d is after freeCompilerArgs because we don't allow overriding the output dir (it breaks task dependencies)
    // (specifying -d multiple times generates a warning, and only the last value is used)
    // TODO forbid -d in freeCompilerArgs in the frontend, so it's clearer for the users
    add("-d")
    add(outputPath.pathString)
}

context(CompileTask)
internal fun kotlinNativeCompilerArgs(
    kotlinUserSettings: KotlinUserSettings,
    compilerPlugins: List<CompilerPlugin>,
    entryPoint: String?,
    libraryPaths: List<Path>,
    sourceFiles: List<Path>,
    outputPath: Path,
    isFramework: Boolean,
): List<String> = buildList {
    if (kotlinUserSettings.debug) {
        add("-g")
    }

    add("-ea")

    add("-produce")
    if (module.type.isLibrary() && !isTest) add("library")
    else if (!isTest && isFramework) add("framework")
    else add("program")

    // TODO full module path including entire hierarchy? -Xshort-module-name)
    add("-module-name")
    add(module.userReadableName)

    add("-target")
    add(platform.name.lowercase())

    if (isTest) {
        add("-generate-test-runner")
    }

    if (entryPoint != null) {
        add("-entry")
        add(entryPoint)
    }

    libraryPaths.forEach {
        add("-library")
        add(it.pathString)
    }

    // Common args last, because they contain free compiler args
    addAll(kotlinCommonCompilerArgs(isMultiplatform = true, kotlinUserSettings, compilerPlugins))

    // -output is after freeCompilerArgs because we don't allow overriding the output dir (it breaks task dependencies)
    // TODO forbid -output in freeCompilerArgs in the frontend, so it's clearer for the users
    add("-output")
    add(outputPath.pathString)

    addAll(sourceFiles.map { it.pathString })
}

// https://github.com/JetBrains/kotlin/blob/v1.9.23/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/K2MetadataCompilerArguments.kt
internal fun kotlinMetadataCompilerArgs(
    kotlinUserSettings: KotlinUserSettings,
    moduleName: String,
    classpath: List<Path>,
    compilerPlugins: List<CompilerPlugin>,
    outputPath: Path,
    friendPaths: List<Path>,
    refinesPaths: List<Path>,
    sourceFiles: List<Path>,
): List<String> = buildList {
    // TODO full module path including entire hierarchy? -Xshort-module-name)
    add("-module-name")
    add(moduleName)

    add("-classpath")
    add(classpath.joinToString(File.pathSeparator))

    if (friendPaths.isNotEmpty()) {
        // KT-34277 Kotlinc processes -Xfriend-paths differently for Javascript vs. JVM, using different list separators
        // https://github.com/JetBrains/kotlin/blob/4964ee12a994bc846ecdb4810486aaf659be00db/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/K2JVMCompilerArguments.kt#L531
        add("-Xfriend-paths=${friendPaths.joinToString(",")}")
    }

    if (refinesPaths.isNotEmpty()) {
        add("-Xrefines-paths=${friendPaths.joinToString(",")}")
    }

    // Common args last, because they contain free compiler args
    addAll(kotlinCommonCompilerArgs(isMultiplatform = true, kotlinUserSettings, compilerPlugins))

    // -d is after freeCompilerArgs because we don't allow overriding the output dir (it breaks task dependencies)
    // (specifying -d multiple times generates a warning, and only the last value is used)
    // TODO forbid -d in freeCompilerArgs in the frontend, so it's clearer for the users
    add("-d")
    add(outputPath.pathString)
    
    addAll(sourceFiles.map { it.pathString })
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
