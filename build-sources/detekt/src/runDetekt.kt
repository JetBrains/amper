/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.detekt

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

@TaskAction
fun runDetectForOutput(
    @Input commonParameters: CommonRunDetectSettings,
    @Input(inferTaskDependency = false) baselineFile: Path,
    @Output outputXmlReport: Path,
) = runDetekt(
    commonParameters = commonParameters,
    baselineFile = baselineFile,
    outputXmlReport = outputXmlReport,
    generateBaseline = false,
)

@TaskAction
fun runDetectForBaseline(
    @Input commonParameters: CommonRunDetectSettings,
    @Output outputBaselineFile: Path,
) = runDetekt(
    commonParameters = commonParameters,
    baselineFile = outputBaselineFile,
    outputXmlReport = null,
    generateBaseline = true,
)

private fun runDetekt(
    commonParameters: CommonRunDetectSettings,
    baselineFile: Path,
    outputXmlReport: Path?,
    generateBaseline: Boolean,
) {
    val reportDiagnostics = !generateBaseline

    if (commonParameters.sources.sourceDirectories.isEmpty()) {
        return
    }

    val inputDirs = commonParameters.sources.sourceDirectories
        .filter { it.isDirectory() }
        .joinToString(File.pathSeparator)

    outputXmlReport?.createParentDirectories()

    // detekt uses output with the same --baseline argument

    val args = mutableListOf<String>().apply {
        add(DETEKT_MAIN_CLASS)
        add("--input")
        add(inputDirs)

        // Provide classpath for type resolution if available
        val cp = commonParameters.moduleClasspath.resolvedFiles
        if (cp.isNotEmpty()) {
            add("--classpath")
            add(cp.joinToString(File.pathSeparator))
        }

        commonParameters.settings.configFile?.let {
            add("--config")
            add(it.pathString)
        }

        if (commonParameters.settings.buildUponDefaultConfig) {
            add("--build-upon-default-config")
        }

        if (generateBaseline) {
            baselineFile.createParentDirectories()
            add("--create-baseline")
            add("--baseline")
            add(baselineFile.pathString)
        }

        if (!generateBaseline && baselineFile.isRegularFile()) {
            add("--baseline")
            add(baselineFile.pathString)
        }

        if (outputXmlReport != null) {
            add("--report")
            add("xml:${outputXmlReport}")
        }
    }

    // Build Java command line that launches Detekt CLI inside a separate process
    val detektCp = commonParameters.detektClasspath.resolvedFiles.joinToString(File.pathSeparator)
    val commandLine = buildList {
        add(ProcessHandle.current().info().command().orElse("java"))
        add("-cp")
        add(detektCp)
        addAll(args)
    }

    // FIXME: AMPER-4912 Introduce a process launching facility (shared with other plugins)
    val process = ProcessBuilder(commandLine)
        // detekt doesn't work well on our JRE - it uses deprecated JVM features; discard these messages.
        // TODO: update to detect 2.0.0 when it is released
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .redirectOutput(if (reportDiagnostics) ProcessBuilder.Redirect.PIPE else ProcessBuilder.Redirect.DISCARD)
        .start()
    val capture = if (reportDiagnostics) {
        thread { process.inputStream.copyTo(System.out) }
    } else null

    val exitCode = process
        .waitFor()

    capture?.join()

    if (reportDiagnostics) {
        process.inputStream.copyTo(System.err)

        check(exitCode == 0) {
            "Detekt terminated with code = $exitCode. See the log above for details."
        }
    }
}

private const val DETEKT_MAIN_CLASS = "io.gitlab.arturbosch.detekt.cli.Main"
