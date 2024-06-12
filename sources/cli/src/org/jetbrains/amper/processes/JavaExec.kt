/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.Jdk
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.diagnostics.setListAttribute
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Runs a Java program with the runtime of this [Jdk].
 */
suspend fun Jdk.runJava(
    workingDir: Path,
    mainClass: String,
    classpath: List<Path>,
    programArgs: List<String>,
    jvmArgs: List<String> = emptyList(),
    environment: Map<String, String> = emptyMap(),
    outputListener: ProcessOutputListener,
): ProcessResult {
    val classpathStr = classpath.joinToString(File.pathSeparator) { it.pathString }
    val command = buildList {
        add(javaExecutable.pathString)

        if (classpath.isNotEmpty()) {
            add("-cp")
            add(classpathStr)
        }

        addAll(jvmArgs)
        add(mainClass)

        addAll(programArgs)
    }
    return spanBuilder("java-exec")
        .setAttribute("java-executable", javaExecutable.pathString)
        .setAttribute("java-version", version)
        .setListAttribute("jvm-args", jvmArgs)
        .setListAttribute("env-vars", environment.map { "${it.key}=${it.value}" }.sorted())
        .setAttribute("classpath", classpathStr)
        .setAttribute("main-class", mainClass)
        .useWithScope { span ->
            BuildPrimitives.runProcessAndGetOutput(
                workingDir,
                *command.toTypedArray(),
                environment = environment,
                span = span,
                outputListener = outputListener,
            )
        }
}
