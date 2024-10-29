/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.amper.core.system.OsFamily
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ProcessesTest {
    private val unknownCommandExitCode = if (OsFamily.current.isWindows) 1 else 127
    private val cancelledExitCode = if (OsFamily.current.isWindows) 1 else 137
    private val loremIpsum1000 =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus nibh odio, auctor non tincidunt eu, posuere vitae nisl. Sed lobortis gravida sapien, eget feugiat purus feugiat et. Fusce ullamcorper risus ac diam varius, ullamcorper molestie est aliquam. Ut dictum, tellus sit amet efficitur hendrerit, est dolor bibendum nunc, et lacinia sem erat nec lectus. Donec orci elit, feugiat in arcu vel, dictum ultricies diam. Nullam ut ultricies tortor. Sed a finibus tortor. Vestibulum et diam vitae orci hendrerit faucibus ac posuere leo. Nunc laoreet interdum euismod. Pellentesque ac porttitor enim. In malesuada pharetra orci in euismod. Quisque sit amet rutrum enim. Morbi ultrices blandit augue, non tincidunt sapien sagittis sit amet. Mauris id tempus tortor, vitae ullamcorper orci. Phasellus efficitur dolor mollis, mattis lacus quis, convallis elit. Phasellus dignissim, nibh a aliquam commodo, ipsum risus suscipit massa, et porta lacus eros nec felis. Nulla ante augue, elementum cras amet."

    @Test
    fun `runProcessAndCaptureOutput should capture stdout and stderr`() = runBlocking(Dispatchers.IO) {
        val command = when (OsFamily.current) {
            // there doesn't seem to be a way to have a line break in the middle of a single echo in Windows batch,
            // so we don't really test it here (https://stackoverflow.com/questions/132799)
            OsFamily.Windows -> cmd("@echo line1&& @echo line2&& @echo break&& @echo hello stderr 1>&2")
            else -> binSh("printf 'line1\n'; printf 'line2\nbreak'; printf 'hello stderr' 1>&2")
        }
        val result = runProcessAndCaptureOutput(command = command)
        assertZeroExitCode(result)
        assertEquals(listOf("line1", "line2", "break"), result.stdout.trim().lines())
        assertEquals("hello stderr", result.stderr.trim())
    }

    @Test
    fun `runProcessAndCaptureOutput should capture stderr in case of wrong nested command`() = runBlocking(Dispatchers.IO) {
        val command = when (OsFamily.current) {
            OsFamily.Windows -> cmd("@echo line1 && not-a-command")
            else -> binSh("echo line1; not-a-command")
        }
        val result = runProcessAndCaptureOutput(command = command)
        assertEquals(unknownCommandExitCode, result.exitCode)
        assertEquals("line1", result.stdout.trim())
        assertContains(result.stderr, "not-a-command")

        val expectedError = when (OsFamily.current) {
            OsFamily.Windows -> "is not recognized as an internal or external command"
            else -> "not found"
        }
        assertContains(result.stderr, expectedError)
    }

    // We don't want to crash if the process is killed externally, we want to read its exit code and stdout/stderr,
    // and possibly report errors as we want.
    @Test
    fun `awaitListening should terminate normally if the process is killed externally`() = runBlocking(Dispatchers.IO) {
        val process = ProcessBuilder(echoLoop(n = 100000, message = loremIpsum1000)).start()

        val firstOutputEvent = CompletableDeferred<Unit>()
        val capture = ProcessOutputListener.InMemoryCapture()

        val deferredExitCode = async {
            process.awaitListening(
                outputListener = capture + object : ProcessOutputListener {
                    override fun onStdoutLine(line: String, pid: Long) {
                        firstOutputEvent.complete(Unit)
                    }

                    override fun onStderrLine(line: String, pid: Long) {
                        firstOutputEvent.complete(Unit)
                    }
                }
            )
        }

        // make sure we got some output from the process, confirming it is running
        firstOutputEvent.await()

        // simulate external kill via regular API
        process.destroyForcibly()
        process.waitFor(1, TimeUnit.SECONDS)
        assertTerminated(process, "The process should have terminated by now, because it was explicitly killed")

        val exitCode = withTimeoutOrNull(200.milliseconds) { deferredExitCode.await() }
        assertNotNull(exitCode, "The result should be returned quickly after the destruction of the process")

        // We don't assert anything on stderr, because the way the process is killed may lead to unpredictable stderr.
        // For example, on Windows, there seems to be races between the cleanup of the standard streams pipes and the
        // death of the process, leading to errors like: "The process tried to write to a nonexistent pipe".
        assertTrue(capture.stdout.startsWith(loremIpsum1000), "At least the first line of output should have been captured, but got: ${capture.stdout}")
        assertEquals(cancelledExitCode, exitCode, "The exit code should be the cancellation exit code $cancelledExitCode")
    }

    @Test
    fun `should transfer custom env`() = runBlocking(Dispatchers.IO) {
        val result = runProcessAndCaptureOutput(
            command = echoEnv("MY_ENV"),
            environment = mapOf("MY_ENV" to "env_value"),
        )
        assertZeroExitCode(result)
        assertEquals("env_value", result.stdout.trim())
        assertEquals("", result.stderr)
    }

    @Test
    fun `withGuaranteedTermination should kill the process when cancelled`() = runBlocking(Dispatchers.IO) {
        val process = ProcessBuilder(binSh("sleep 10")).start()

        val time = measureTime {
            // simulate quick cancellation (cannot be 0ms otherwise the block is not run at all)
            withTimeoutOrNull(1.milliseconds) {
                process.withGuaranteedTermination {
                    // no need for anything in the body
                }
            }
        }
        assertTerminated(process, "withGuaranteedTermination should not exit before the process is terminated")
        assertTrue(time < 500.milliseconds, "The process should be terminated almost instantly")
    }

    @Test
    fun `withGuaranteedTermination should kill the process when already cancelled`() = runBlocking(Dispatchers.IO) {
        val process = ProcessBuilder(binSh("sleep 10")).start()

        val time = measureTime {
            val launchJob = launch {
                cancel() // simulates that the coroutine is already cancelled before the call
                process.withGuaranteedTermination {
                    fail("The body of withGuaranteedTermination should not be run if the coroutine is already cancelled")
                }
            }
            launchJob.join()
        }
        assertTerminated(process, "withGuaranteedTermination should not exit before the process is terminated")
        assertTrue(time < 500.milliseconds, "The process should be terminated almost instantly")
    }
}

private fun assertTerminated(process: Process, message: String) {
    if (!process.isAlive) {
        return
    }
    process.destroyForcibly()
    val testProcessTerminated = process.waitFor(1, TimeUnit.SECONDS)
    if (!testProcessTerminated) {
        System.err.println("Failed to kill test process ${process.pid()} (still not terminated after 1s)")
    }
    fail("$message. Killed afterwards: $testProcessTerminated")
}

private fun binSh(command: String): List<String> = when (OsFamily.current) {
    OsFamily.Windows -> listOf("bash.exe", "-c", command) // FIXME this relies on bash.exe in System32 (setup by WSL)
    else -> listOf("/bin/sh", "-c", command)
}

private fun echoEnv(envVarName: String) = when (OsFamily.current) {
    OsFamily.Windows -> cmd("@echo %$envVarName%")
    else -> binSh("echo \$$envVarName")
}

private fun echoLoop(n: Int, message: String) = when (OsFamily.current) {
    // Weird hack on Windows just to trim the double quotes while keeping the command valid even with special chars.
    // This is an abuse of the /P flag of the set command.
    OsFamily.Windows -> cmd("for /l %x in (1, 1, $n) do @echo|(@set /P dummy=\"$message\" && echo.)")
    else -> binSh("for i in `seq 1 $n`; do echo '$message'; done")
}

private fun cmd(command: String) = listOf("cmd", "/c", command)

private fun assertZeroExitCode(result: ProcessResult) {
    assertEquals(0, result.exitCode,
        "Process terminated with non-zero exit code ${result.exitCode}. Output:\n" +
                "${result.stdout.prependIndent("stdout>")}\n" +
                "${result.stderr.prependIndent("stderr>")}\n"
    )
}
