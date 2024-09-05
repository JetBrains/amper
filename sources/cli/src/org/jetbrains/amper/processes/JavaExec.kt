/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import com.intellij.execution.CommandLineWrapperUtil
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.Jdk
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.setMapAttribute
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
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
    tempRoot: AmperProjectTempRoot,
    input: ProcessInput? = null,
): ProcessResult {
    val classpathStr = classpath.joinToString(File.pathSeparator) { it.pathString }
    val args = buildList {
        if (classpath.isNotEmpty()) {
            add("-cp")
            add(classpathStr)
        }

        addAll(jvmArgs)
        add(mainClass)

        addAll(programArgs)
    }

    return withJavaArgFile(tempRoot, args) { argFile ->
        spanBuilder("java-exec")
            .setAttribute("java-executable", javaExecutable.pathString)
            .setAttribute("java-version", version)
            .setListAttribute("program-args", programArgs)
            .setListAttribute("jvm-args", jvmArgs)
            .setMapAttribute("env-vars", environment)
            .setAttribute("classpath", classpathStr)
            .setAttribute("main-class", mainClass)
            .useWithScope { span ->
                BuildPrimitives.runProcessAndGetOutput(
                    workingDir = workingDir,
                    command = listOf(javaExecutable.pathString, "@${argFile.pathString}"),
                    environment = environment,
                    span = span,
                    outputListener = outputListener,
                    input = input,
                )
            }
    }
}

inline fun <R> withJavaArgFile(tempRoot: AmperProjectTempRoot, args: List<String>, block: (Path) -> R): R {
    tempRoot.path.createDirectories()
    val argFile = createTempFile(tempRoot.path, "kotlin-args-", ".txt")
    return try {
        CommandLineWrapperUtil.writeArgumentsFile(argFile.toFile(), args, Charsets.UTF_8)
        block(argFile)
    } finally {
        argFile.deleteExisting()
    }
}
