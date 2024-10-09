/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.ksp

import org.jetbrains.amper.compilation.CompilationUserSettings
import org.jetbrains.amper.frontend.schema.JavaVersion
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

internal fun KspJvmConfig(
    kspOutputPaths: KspOutputPaths,
    compilationSettings: CompilationUserSettings,
    block: KspJvmConfig.Builder.() -> Unit = {},
): KspJvmConfig = KspJvmConfig.Builder(kspOutputPaths, compilationSettings).apply(block)

internal fun KspJsConfig(
    kspOutputPaths: KspOutputPaths,
    compilationSettings: CompilationUserSettings,
    block: KspJsConfig.Builder.() -> Unit = {},
): KspJsConfig = KspJsConfig.Builder(kspOutputPaths, compilationSettings).apply(block)

internal fun KspNativeConfig(
    kspOutputPaths: KspOutputPaths,
    compilationSettings: CompilationUserSettings,
    block: KspNativeConfig.Builder.() -> Unit = {},
): KspNativeConfig = KspNativeConfig.Builder(kspOutputPaths, compilationSettings).apply(block)

internal fun KspCommonConfig(
    kspOutputPaths: KspOutputPaths,
    compilationSettings: CompilationUserSettings,
    block: KspCommonConfig.Builder.() -> Unit = {},
): KspCommonConfig = KspCommonConfig.Builder(kspOutputPaths, compilationSettings).apply(block)

internal sealed interface KspConfig {
    /**
     * The unique name of the module. KSP doesn't use it itself, it just passes it to the Kotlin compiler.
     */
    val moduleName: String
    val sourceRoots: List<Path>
    val commonSourceRoots: List<Path>
    val libraries: List<Path>

    val processorOptions: Map<String, String>
    val languageVersion: String
    val apiVersion: String
    val allWarningsAsErrors: Boolean
    val mapAnnotationArgumentsInJava: Boolean // TODO find what that means

    val incremental: Boolean
    val incrementalLog: Boolean
    val modifiedSources: List<Path>
    val removedSources: List<Path>
    val changedClasses: List<String>

    /** The base dir of the module, used to relativize paths for incrementality processing and output tracking. */
    val projectBaseDir: Path

    /** The directory where KSP can store its ephemeral caches. */
    val cachesDir: Path

    /**
     * The common parent of all output dirs, used to relativize paths to replicate the relative outputs hierarchy into
     * a backup directory.
     */
    val outputBaseDir: Path

    /** The output directory for generated classes. */
    val classOutputDir: Path

    /** The output directory for generated Kotlin sources. */
    val kotlinOutputDir: Path

    /** The output directory for generated resources. */
    val resourceOutputDir: Path

    fun toCommandLineOptions(workDir: Path, legacyListMode: Boolean) = buildList {
        add("-module-name=$moduleName")
        addPaths("-source-roots", sourceRoots, workDir, legacyListMode)
        addPaths("-common-source-roots", commonSourceRoots, workDir, legacyListMode)
        addPaths("-libraries", libraries, workDir, legacyListMode)

        addList("-processor-options", processorOptions.map { "${it.key}=${it.value}" }, legacyListMode)
        add("-language-version=$languageVersion")
        add("-api-version=$apiVersion")
        add("-all-warnings-as-errors=$allWarningsAsErrors")
        add("-map-annotation-arguments-in-java=$mapAnnotationArgumentsInJava")

        add("-incremental=$incremental")
        add("-incremental-log=$incrementalLog")
        addPaths("-modified-sources", modifiedSources, workDir, legacyListMode)
        addPaths("-removed-sources", removedSources, workDir, legacyListMode)
        addList("-changed-classes", changedClasses, legacyListMode)

        add("-project-base-dir=${projectBaseDir.pathString}")
        add("-output-base-dir=${outputBaseDir.pathString}")
        add("-caches-dir=${cachesDir.pathString}")
        add("-class-output-dir=${classOutputDir.pathString}")
        add("-kotlin-output-dir=${kotlinOutputDir.pathString}")
        add("-resource-output-dir=${resourceOutputDir.pathString}")
    }

    sealed class Builder(
        kspOutputPaths: KspOutputPaths,
        compilationSettings: CompilationUserSettings,
    ) : KspConfig {
        override lateinit var moduleName: String

        override lateinit var sourceRoots: List<Path>
        override var commonSourceRoots: List<Path> = emptyList()

        // No default provided here, because forgetting libraries and causing compiler errors makes KSP silently fail,
        // with absolutely 0 logs.
        override lateinit var libraries: List<Path>

        override var processorOptions: Map<String, String> = emptyMap()
        override var languageVersion: String = compilationSettings.kotlin.languageVersion.schemaValue
        override var apiVersion: String = compilationSettings.kotlin.apiVersion.schemaValue
        override var allWarningsAsErrors: Boolean = compilationSettings.kotlin.allWarningsAsErrors
        override var mapAnnotationArgumentsInJava: Boolean = false // TODO map this to a setting?

        override var incremental: Boolean = false
        override var incrementalLog: Boolean = false
        override var modifiedSources: List<Path> = emptyList()
        override var removedSources: List<Path> = emptyList()
        override var changedClasses: List<String> = emptyList()

        override var projectBaseDir: Path = kspOutputPaths.moduleBaseDir
        override var cachesDir: Path = kspOutputPaths.cachesDir
        override var outputBaseDir: Path = kspOutputPaths.outputsBaseDir
        override var classOutputDir: Path = kspOutputPaths.classesDir
        override var kotlinOutputDir: Path = kspOutputPaths.kotlinSourcesDir
        override var resourceOutputDir: Path = kspOutputPaths.resourcesDir
    }

    companion object {
        fun needsLegacyListMode(kspVersion: String) = kspVersion.endsWith("1.0.25")
    }
}

private fun MutableList<String>.addList(argName: String, values: List<String>, legacyListMode: Boolean) {
    if (values.isNotEmpty()) {
        val sep = if (legacyListMode) ":" else File.pathSeparator
        add("$argName=${values.joinToString(sep)}")
    }
}

private fun MutableList<String>.addPaths(argName: String, paths: List<Path>, workDir: Path, legacyListMode: Boolean) {
    // TODO stop doing that when KSP 1.0.26 is released and our default version is bumped
    val effectivePaths = if (legacyListMode) {
        // We relativize paths to avoid issues with absolute windows paths split on ':'
        // See: https://github.com/google/ksp/issues/2046
        paths.map { it.relativeTo(workDir).pathString }
    } else {
        paths.map { it.pathString }
    }
    addList(argName, effectivePaths, legacyListMode)
}

internal interface KspJvmConfig : KspConfig {
    /**
     * Source roots for Java source files.
     *
     * Only files with the `.java` extension are considered in the given directories, so it's safe to pass the same
     * directory in both [javaSourceRoots] and [sourceRoots].
     */
    val javaSourceRoots: List<Path>

    /**
     * The output directory for generated Java sources.
     *
     * Only files with `.java` extension (at any depth) are passed to Java compilation tasks, so it might be safe to
     * pass the same dir as for Kotlin sources. However, in the KSP Gradle plugin, distinct directories are always used.
     */
    val javaOutputDir: Path

    /**
     * The JDK home path that KSP passes to the Kotlin compiler.
     *
     * **WARNING: This is mandatory, otherwise KSP may crash with weird exceptions.**
     */
    val jdkHome: Path
    val jvmTarget: String
    val jvmDefaultMode: String

    override fun toCommandLineOptions(workDir: Path, legacyListMode: Boolean) = buildList {
        addAll(super.toCommandLineOptions(workDir, legacyListMode))
        addPaths("-java-source-roots", javaSourceRoots, workDir, legacyListMode)
        add("-java-output-dir=${javaOutputDir.pathString}")
        add("-jdk-home=$jdkHome")
        add("-jvm-target=$jvmTarget")
        add("-jvm-default-mode=$jvmDefaultMode")
    }

    class Builder(
        kspOutputPaths: KspOutputPaths,
        compilationSettings: CompilationUserSettings,
    ) : KspConfig.Builder(kspOutputPaths, compilationSettings), KspJvmConfig {
        override var javaSourceRoots: List<Path> = emptyList()
        override var javaOutputDir: Path = kspOutputPaths.javaSourcesDir

        // The jvmRelease setting is null if the user intentionally disables it because they want the compiler default.
        // This is why we choose 1.8 as a default here: we need to match Kotlin's current default.
        override var jvmTarget: String = (compilationSettings.jvmRelease ?: JavaVersion.VERSION_8).legacyNotation

        override lateinit var jdkHome: Path

        // Here, we follow the behavior of the KSP Gradle plugin
        // (see https://github.com/google/ksp/blob/5e4768d23b6aa8860191d85d68b8419da7527338/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L242-L247)
        override var jvmDefaultMode: String = compilationSettings.kotlin.freeCompilerArgs
            .lastOrNull { it.startsWith("-Xjvm-default=") }
            ?.substringAfter("=")
            ?: "disable"
    }
}

internal interface KspNativeConfig : KspConfig {
    val targetName: String

    override fun toCommandLineOptions(workDir: Path, legacyListMode: Boolean) = buildList {
        addAll(super.toCommandLineOptions(workDir, legacyListMode))
        add("-target=$targetName")
    }

    class Builder(
        kspOutputPaths: KspOutputPaths,
        compilationSettings: CompilationUserSettings,
    ) : KspConfig.Builder(kspOutputPaths, compilationSettings), KspNativeConfig {
        override lateinit var targetName: String
    }
}

internal interface KspJsConfig : KspConfig {
    val backend: WebBackend

    override fun toCommandLineOptions(workDir: Path, legacyListMode: Boolean) = buildList {
        addAll(super.toCommandLineOptions(workDir, legacyListMode))
        add("-backend=${backend.argValue}")
    }

    class Builder(
        kspOutputPaths: KspOutputPaths,
        compilationSettings: CompilationUserSettings,
    ) : KspConfig.Builder(kspOutputPaths, compilationSettings), KspJsConfig {
        override lateinit var backend: WebBackend
    }
}

enum class WebBackend(val argValue: String) {
    JS("JS"),
    WASM("WASM");
}

internal interface KspCommonConfig : KspConfig {
    /**
     * Currently unused by KSP, but mandatory...
     */
    val targets: List<String>

    override fun toCommandLineOptions(workDir: Path, legacyListMode: Boolean) = buildList {
        addAll(super.toCommandLineOptions(workDir, legacyListMode))
        addList("-targets", targets, legacyListMode)
    }

    class Builder(
        kspOutputPaths: KspOutputPaths,
        compilationSettings: CompilationUserSettings,
    ) : KspConfig.Builder(kspOutputPaths, compilationSettings), KspCommonConfig {
        override var targets: List<String> = listOf("dummy") // this is not actually used by KSP for now
    }
}
