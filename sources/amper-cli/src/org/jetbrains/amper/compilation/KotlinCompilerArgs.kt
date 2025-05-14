/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.schema.KotlinVersion
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.ios.IosConventions
import org.jetbrains.amper.util.BuildType
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString
import kotlin.io.path.writeText

private fun kotlinCommonCompilerArgs(
    isMultiplatform: Boolean,
    kotlinUserSettings: KotlinUserSettings,
    fragments: List<Fragment>,
    additionalSourceRoots: List<SourceRoot>,
    compilerPlugins: List<ResolvedCompilerPlugin>,
): List<String> = buildList {
    if (isMultiplatform) {
        add("-Xmulti-platform")

        if (kotlinUserSettings.languageVersion >= KotlinVersion.Kotlin20) {
            fragments.forEach { fragment ->
                add("-Xfragments=${fragment.name}")
                add("-Xfragment-sources=${fragment.name}:${fragment.src}")

                fragment.fragmentDependencies.filter { it.type == FragmentDependencyType.REFINE }.forEach {
                    add("-Xfragment-refines=${fragment.name}:${it.target.name}")
                }
            }
            // for generated sources that must be associated with existing fragments
            additionalSourceRoots.forEach { sourceRoot ->
                add("-Xfragment-sources=${sourceRoot.fragmentName}:${sourceRoot.path.absolutePathString()}")
            }
        }
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
    if (kotlinUserSettings.storeJavaParameterNames) {
        add("-java-parameters")
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
        // Note: this is technically wrong, because we resolve each compiler plugin classpath independently, but the
        // kotlin compiler loads all plugin classpaths together in a single classloader (so we don't have cross-plugin
        // conflict resolution). At the moment, all compiler plugins have a single jar, so this is not a problem.
        // The proper way would be to resolve all plugins in a single resolution scope to resolve conflicts.
        // However, the new -Xcompiler-plugin argument will actually work with one classpath per plugin, loaded in
        // independent classloaders, so we would have to change it back when doing the switch, so let's wait instead.
        plugin.classpath.forEach { path ->
            add("-Xplugin=${path.absolutePathString()}")
        }
        plugin.options.forEach { opt ->
            add("-P")
            add("plugin:${plugin.id}:${opt.name}=${opt.value}")
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
    compilerPlugins: List<ResolvedCompilerPlugin>,
    jdkHome: Path,
    outputPath: Path,
    friendPaths: List<Path>,
    fragments: List<Fragment>,
    additionalSourceRoots: List<SourceRoot>,
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
    addAll(kotlinCommonCompilerArgs(
        isMultiplatform = isMultiplatform,
        kotlinUserSettings = userSettings.kotlin,
        fragments = fragments,
        additionalSourceRoots = additionalSourceRoots,
        compilerPlugins = compilerPlugins,
    ))

    // -d is after freeCompilerArgs because we don't allow overriding the output dir (it breaks task dependencies)
    // (specifying -d multiple times generates a warning, and only the last value is used)
    // TODO forbid -d in freeCompilerArgs in the frontend, so it's clearer for the users
    add("-d")
    add(outputPath.pathString)
}

enum class KotlinCompilationType(val argName: String) {
    LIBRARY("library"),
    BINARY("program"),
    IOS_FRAMEWORK("framework");

    fun outputFilename(module: AmperModule, platform: Platform, isTest: Boolean): String = when {
        this == LIBRARY -> "${moduleName(module, isTest)}.klib"
        this == IOS_FRAMEWORK -> IosConventions.KOTLIN_FRAMEWORK_NAME  // TODO: Allow customization
        this == BINARY && platform.isDescendantOf(Platform.MINGW) -> "${moduleName(module, isTest)}.exe"
        else -> "${moduleName(module, isTest)}.kexe"
    }

    fun moduleName(module: AmperModule, isTest: Boolean): String = when(this) {
        IOS_FRAMEWORK -> if (isTest) module.nameWithoutDashes + "Test" else module.nameWithoutDashes
        else -> module.kotlinModuleName(isTest)
    }

    private val AmperModule.nameWithoutDashes get() = userReadableName.replace("-", "").replace("_", "")
}

// TODO should we make it unique by using the full path?
internal fun AmperModule.kotlinModuleName(isTest: Boolean) =
    if (isTest) userReadableName + "_test" else userReadableName

context(BuildTask)
internal fun kotlinNativeCompilerArgs(
    buildType: BuildType,
    kotlinUserSettings: KotlinUserSettings,
    compilerPlugins: List<ResolvedCompilerPlugin>,
    entryPoint: String?,
    libraryPaths: List<Path>,
    exportedLibraryPaths: List<Path>,
    fragments: List<Fragment>,
    sourceFiles: List<Path>,
    additionalSourceRoots: List<SourceRoot>,
    binaryOptions: Map<String, String>,
    outputPath: Path,
    compilationType: KotlinCompilationType,
    include: Path?,
): List<String> = buildList {
    if (kotlinUserSettings.debug ?: (buildType == BuildType.Debug)) {
        add("-g")
    }
    if (kotlinUserSettings.optimization ?: (buildType == BuildType.Release)) {
        add("-opt")
    }

    add("-ea")

    add("-produce")
    add(compilationType.argName)

    // TODO full module path including entire hierarchy? -Xshort-module-name)
    add("-module-name")
    add(compilationType.moduleName(module, isTest))

    add("-target")
    add(platform.nameForCompiler)

    if (compilationType != KotlinCompilationType.LIBRARY) {
        if (isTest) {
            add("-generate-test-runner")
        } else {
            if (entryPoint != null) {
                add("-entry")
                add(entryPoint)
            }
        }
    }

    if (compilationType == KotlinCompilationType.IOS_FRAMEWORK) {
        // We always link the framework statically
        add("-Xstatic-framework")
    }

    binaryOptions.forEach { (key, value) ->
        add("-Xbinary=$key=$value")
    }

    libraryPaths.forEach {
        add("-library")
        add(it.pathString)
    }

    exportedLibraryPaths.forEach {
        add("-Xexport-library=${it.pathString}")
    }

    // Common args last, because they contain free compiler args
    addAll(kotlinCommonCompilerArgs(
        isMultiplatform = true,
        kotlinUserSettings = kotlinUserSettings,
        fragments = fragments,
        additionalSourceRoots = additionalSourceRoots,
        compilerPlugins = compilerPlugins,
    ))

    if (include != null) add("-Xinclude=${include.pathString}")

    // -output is after freeCompilerArgs because we don't allow overriding the output dir (it breaks task dependencies)
    // TODO forbid -output in freeCompilerArgs in the frontend, so it's clearer for the users
    add("-output")
    add(outputPath.pathString)

    // Skip adding sources to `IOS_FRAMEWORK`, since it will use `-Xinclude` argument instead.
    if (compilationType != KotlinCompilationType.IOS_FRAMEWORK) {
        addAll(sourceFiles.map { it.pathString })
    }
}

// https://github.com/JetBrains/kotlin/blob/v1.9.23/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/K2MetadataCompilerArguments.kt
internal fun kotlinMetadataCompilerArgs(
    kotlinUserSettings: KotlinUserSettings,
    moduleName: String,
    classpath: List<Path>,
    compilerPlugins: List<ResolvedCompilerPlugin>,
    outputPath: Path,
    friendPaths: List<Path>,
    refinesPaths: List<Path>,
    fragments: List<Fragment>,
    sourceFiles: List<Path>,
    additionalSourceRoots: List<SourceRoot>,
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
        add("-Xrefines-paths=${refinesPaths.joinToString(",")}")
    }

    // Common args last, because they contain free compiler args
    addAll(kotlinCommonCompilerArgs(
        isMultiplatform = true,
        kotlinUserSettings = kotlinUserSettings,
        fragments = fragments,
        additionalSourceRoots = additionalSourceRoots,
        compilerPlugins = compilerPlugins,
    ))

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
    val argFile = createTempFile(tempRoot.path, "kotlin-args-", ".txt")
    return try {
        argFile.writeText(argString)
        block(argFile)
    } finally {
        argFile.deleteExisting()
    }
}
