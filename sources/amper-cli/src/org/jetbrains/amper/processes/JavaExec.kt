/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import com.intellij.execution.CommandLineWrapperUtil
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.setMapAttribute
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString

/**
 * Defines how to pass arguments to the Java process, including the classpath, main class, program arguments, and other
 * JVM arguments.
 */
sealed class ArgsMode {

    /**
     * Passes arguments as regular command line arguments.
     *
     * This can sometimes exceed the maximum length of the command line (especially on Windows), so it is not
     * recommended unless you know the classpath is small and the arguments are few and short.
     */
    data object CommandLine : ArgsMode()

    /**
     * Uses the Java argfile mechanism to pass arguments.
     *
     * The arguments are written to a temporary file in the given [tempRoot], and only the reference to the file is
     * passed as argument to the Java process.
     */
    data class ArgFile(val tempRoot: AmperProjectTempRoot) : ArgsMode()
}

/**
 * Runs a Java program with the runtime of this [Jdk].
 */
suspend fun Jdk.runJava(
    workingDir: Path,
    mainClass: String,
    classpath: List<Path>,
    programArgs: List<String>,
    argsMode: ArgsMode,
    jvmArgs: List<String> = emptyList(),
    environment: Map<String, String> = emptyMap(),
    outputListener: ProcessOutputListener,
    input: ProcessInput = ProcessInput.Empty,
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

    return spanBuilder("java-exec")
        .setAttribute("java-executable", javaExecutable.pathString)
        .setAttribute("java-version", version)
        .setListAttribute("program-args", programArgs)
        .setListAttribute("jvm-args", jvmArgs)
        .setMapAttribute("env-vars", environment)
        .setAttribute("classpath", classpathStr)
        .setAttribute("main-class", mainClass)
        .use { span ->
            argsMode.withEffectiveArgs(args) { effectiveArgs ->
                BuildPrimitives.runProcessAndGetOutput(
                    workingDir = workingDir,
                    command = listOf(javaExecutable.pathString) + effectiveArgs,
                    environment = environment,
                    span = span,
                    outputListener = outputListener,
                    input = input,
                )
            }
        }
}

private inline fun <R> ArgsMode.withEffectiveArgs(args: List<String>, block: (List<String>) -> R): R = when (this) {
    ArgsMode.CommandLine -> block(args)
    is ArgsMode.ArgFile -> withJavaArgFile(tempRoot, args) { argFilePath ->
        block(listOf("@${argFilePath.pathString}"))
    }
}

inline fun <R> withJavaArgFile(tempRoot: AmperProjectTempRoot, args: List<String>, block: (Path) -> R): R {
    tempRoot.path.createDirectories()
    val argFile = createTempFile(tempRoot.path, "java-args-", ".txt")
    return try {
        CommandLineWrapperUtil.writeArgumentsFile(argFile.toFile(), args, Charsets.UTF_8)
        block(argFile)
    } finally {
        argFile.deleteExisting()
    }
}
