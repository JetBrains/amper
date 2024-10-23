/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PROCESS_BUILDER_START_LEAK")

package org.jetbrains.amper.processes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * The result of a completed process.
 */
class ProcessResult(
    /** The exit code of the process. */
    val exitCode: Int,
    /** The whole standard output of the process, decoded as UTF-8 text. */
    val stdout: String,
    /** The whole standard error stream of the process, decoded as UTF-8 text. */
    val stderr: String,
)

/**
 * Starts a new process with the given [command] in [workingDir], and awaits the result.
 * While waiting, stdout and stderr are sent to the given [outputListener], but are also fully captured in memory, so
 * they can be returned as a [ProcessResult]. Make sure the process doesn't output too much data.
 *
 * The given [input] is used to send data to the standard input of the started process.
 *
 * If the current coroutine is cancelled before the process has terminated, the process is killed (first normally then
 * forcibly), and cleans the stream readers. If the process is not killable and hangs, this function will also hang
 * instead of returning (otherwise the zombie process could leak).
 *
 * **Note:** since the blocking reads of standard streams are not cancellable, this function may have to wait for the
 * read of the current line to complete before returning (on cancellation). This is to ensure no coroutines are leaked.
 * This wait should be reasonably short anyway because the process is killed on cancellation, so no more output
 * should be written in that case.
 *
 * If the JVM is terminated gracefully (Ctrl+C / SIGINT), this function **requests the process destruction** but doesn't
 * wait for its completion (we mustn't block the JVM shutdown).
 */
suspend fun runProcessAndCaptureOutput(
    workingDir: Path? = null,
    command: List<String>,
    environment: Map<String, String> = emptyMap(),
    redirectErrorStream: Boolean = false,
    input: ProcessInput = ProcessInput.Empty,
    outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
): ProcessResult {
    val capture = ProcessOutputListener.InMemoryCapture()
    val exitCode = runProcess(
        workingDir = workingDir,
        command = command,
        environment = environment,
        redirectErrorStream = redirectErrorStream,
        outputListener = outputListener + capture,
        input = input,
    )
    return ProcessResult(exitCode = exitCode, stdout = capture.stdout, stderr = capture.stderr)
}

/**
 * Starts a new process with the given [command] in [workingDir], and awaits its completion.
 * While waiting, stdout and stderr are sent to the given [outputListener].
 *
 * The given [input] is used to send data to the standard input of the started process.
 *
 * If the current coroutine is cancelled before the process has terminated, the process is killed (first normally then
 * forcibly), and the stream readers cleaned up. If the process is not killable and hangs, this function will also hang
 * instead of returning (otherwise the zombie process could leak).
 *
 * **Note:** since the blocking reads of standard streams are not cancellable, this function may have to wait for the
 * read of the current line to complete before returning (on cancellation). This is to ensure no coroutines are leaked.
 * This wait should be reasonably short anyway because the process is killed on cancellation, so no more output
 * should be written in that case.
 *
 * If the JVM is terminated gracefully (Ctrl+C / SIGINT), this function **requests the process destruction** but doesn't
 * wait for its completion (we mustn't block the JVM shutdown).
 */
suspend fun runProcess(
    workingDir: Path? = null,
    command: List<String>,
    environment: Map<String, String> = emptyMap(),
    redirectErrorStream: Boolean = false,
    outputListener: ProcessOutputListener,
    input: ProcessInput = ProcessInput.Empty,
): Int {
    require(command.isNotEmpty()) { "Cannot start a process with an empty command line" }

    val exitCode = withContext(Dispatchers.IO) {
        process(workingDir, command, environment, redirectErrorStream)
            .redirectInput(input.stdinRedirection)
            .start()
            .withGuaranteedTermination { process ->
                input.writeTo(process.outputStream)
                process.outputStream.close()
                process.awaitListening(outputListener)
            }
    }
    return exitCode
}

/**
 * Starts a new process with the given [command] in [workingDir], and awaits its completion.
 *
 * While waiting, all standard streams of the child process are inherited from the current process.
 * This function is useful when starting interactive processes that may require user input, or when running processes
 * for which the output should just be printed normally to the console like the rest of the code.
 *
 * If the current coroutine is cancelled before the process has terminated, the process is killed (first normally then
 * forcibly). If the process is not killable and hangs, this function will also hang instead of returning (otherwise
 * the zombie process could leak).
 *
 * If the JVM is terminated gracefully (Ctrl+C / SIGINT), this function **requests the process destruction** but doesn't
 * wait for its completion (we mustn't block the JVM shutdown).
 */
suspend fun runProcessWithInheritedIO(
    workingDir: Path? = null,
    command: List<String>,
    environment: Map<String, String> = emptyMap(),
    redirectErrorStream: Boolean = false,
    onStart: (pid: Long) -> Unit = {},
): Int {
    require(command.isNotEmpty()) { "Cannot start a process with an empty command line" }

    val exitCode = withContext(Dispatchers.IO) {
        process(workingDir, command, environment, redirectErrorStream)
            .inheritIO()
            .start()
            .withGuaranteedTermination { process ->
                process.outputStream.close()
                onStart(process.pid())
                process.onExit().await().exitValue()
            }
    }
    return exitCode
}

@RequiresOptIn("Using this API causes the child process to leak and outlive the execution of the current JVM. " +
        "Make sure you understand the consequences before opting in. " +
        "Please consider limiting the life of the process to at most that of the current JVM by using coroutines.")
annotation class ProcessLeak

/**
 * Starts a new process with the given [command] in [workingDir], detached from the execution of the current JVM.
 *
 * **WARNING:** this new process will not be stopped or awaited by this function call. Only use this function if the
 * intention is to start a long-lived process that survives across executions of this program.
 * In any other case, please prefer other functions that handle coroutines and process lifecycle.
 *
 * @return the started process's PID. This function doesn't return the [Process] object intentionally, because there
 * should be another way to interact with a long-lived process (some kind of IPC).
 */
@ProcessLeak
fun startLongLivedProcess(
    workingDir: Path? = null,
    command: List<String>,
    environment: Map<String, String> = emptyMap(),
    redirectErrorStream: Boolean = false,
): Long { // NOT the Process, intentionally, because there must be some other way to interact with long-lived processes
    return process(
        workingDir = workingDir,
        command = command,
        environment = environment,
        redirectErrorStream = redirectErrorStream
    )
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        // no shutdown hook on purpose - we want to keep the process alive after Amper completes
        .start()
        .apply {
            outputStream.close()
        }
        .pid()
}

private fun process(
    workingDir: Path? = null,
    command: List<String>,
    environment: Map<String, String> = emptyMap(),
    redirectErrorStream: Boolean = false,
): ProcessBuilder {
    require(command.isNotEmpty()) { "Cannot start a process with an empty command line" }

    return ProcessBuilder(command)
        .directory(workingDir?.toFile())
        .also { it.environment().putAll(environment) }
        .redirectErrorStream(redirectErrorStream)
}

private val ProcessInput.stdinRedirection: ProcessBuilder.Redirect
    get() = when (this) {
        ProcessInput.Inherit -> ProcessBuilder.Redirect.INHERIT
        ProcessInput.Empty -> ProcessBuilder.Redirect.PIPE
        is ProcessInput.Text -> ProcessBuilder.Redirect.PIPE
    }

