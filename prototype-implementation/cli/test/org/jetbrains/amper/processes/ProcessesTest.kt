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
import org.jetbrains.amper.util.OS
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
    private val unknownCommandExitCode = 127
    private val cancelledExitCode = if (OS.isWindows) 1 else 137
    private val loremIpsum1000 =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus nibh odio, auctor non tincidunt eu, posuere vitae nisl. Sed lobortis gravida sapien, eget feugiat purus feugiat et. Fusce ullamcorper risus ac diam varius, ullamcorper molestie est aliquam. Ut dictum, tellus sit amet efficitur hendrerit, est dolor bibendum nunc, et lacinia sem erat nec lectus. Donec orci elit, feugiat in arcu vel, dictum ultricies diam. Nullam ut ultricies tortor. Sed a finibus tortor. Vestibulum et diam vitae orci hendrerit faucibus ac posuere leo. Nunc laoreet interdum euismod. Pellentesque ac porttitor enim. In malesuada pharetra orci in euismod. Quisque sit amet rutrum enim. Morbi ultrices blandit augue, non tincidunt sapien sagittis sit amet. Mauris id tempus tortor, vitae ullamcorper orci. Phasellus efficitur dolor mollis, mattis lacus quis, convallis elit. Phasellus dignissim, nibh a aliquam commodo, ipsum risus suscipit massa, et porta lacus eros nec felis. Nulla ante augue, elementum cras amet."

    @Test
    fun `awaitAndGetAllOutput should capture stdout and stderr`() = runBlocking(Dispatchers.IO) {
        val process = ProcessBuilder(binSh("printf '1\n'; printf '2\n'; printf 'line\nbreak'; printf 'hello from stderr' 1>&2")).start()
        val result = process.awaitAndGetAllOutput()
        assertZeroExitCode(result)
        assertEquals("1\n2\nline\nbreak", result.stdout.trim())
        assertEquals("hello from stderr", result.stderr.trim())
    }

    @Test
    fun `awaitAndGetAllOutput should capture stderr in case of wrong nested command`() = runBlocking(Dispatchers.IO) {
        val process = ProcessBuilder(binSh("echo 1; not-a-command")).start()
        val result = process.awaitAndGetAllOutput()
        assertEquals(unknownCommandExitCode, result.exitCode)
        assertEquals("1", result.stdout.trim())
        assertContains(result.stderr, "not-a-command")
        assertContains(result.stderr, "not found")
    }

    // We don't want to crash if the process is killed externally, we want to read its exit code and stdout/stderr,
    // and possibly report errors as we want.
    @Test
    fun `awaitAndGetAllOutput should terminate normally if the process is killed externally`() = runBlocking(Dispatchers.IO) {
        val process = ProcessBuilder(echoLoop(n = 100000, message = loremIpsum1000)).start()

        val firstOutputEvent = CompletableDeferred<Unit>()
        val deferredResult = async {
            process.awaitAndGetAllOutput(
                onStdoutLine = { firstOutputEvent.complete(Unit) },
                onStderrLine = { firstOutputEvent.complete(Unit) },
            )
        }
        // make sure we got some output from the process, confirming it is running
        firstOutputEvent.await()

        // simulate external kill via regular API
        process.destroyForcibly()
        process.waitFor(1, TimeUnit.SECONDS)
        assertTerminated(process, "The process should have terminated by now, because it was explicitly killed")

        val result = withTimeoutOrNull(200.milliseconds) { deferredResult.await() }
        assertNotNull(result) { "The result should be returned quickly after the destruction of the process" }

        assertEquals("", result.stderr, "There should be nothing in stderr")
        assertTrue(result.stdout.startsWith(loremIpsum1000), "At least the first line of output should have been captured, but got: ${result.stdout}")
        assertEquals(cancelledExitCode, result.exitCode, "The exit code should be the cancellation exit code $cancelledExitCode")
    }

    @Test
    fun `should transfer custom env`() = runBlocking(Dispatchers.IO) {
        val process = ProcessBuilder(echoEnv("MY_ENV"))
            .apply { environment().putAll(from = mapOf("MY_ENV" to "env_value")) }
            .start()
        val result = process.awaitAndGetAllOutput()
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

private fun binSh(command: String): List<String> = when (OS.type) {
    OS.Type.Windows -> listOf("bash.exe", "-c", command) // FIXME this relies on bash.exe in System32 (setup by WSL)
    else -> listOf("/bin/sh", "-c", command)
}

private fun echoEnv(envVarName: String) = when (OS.type) {
    OS.Type.Windows -> cmd("@echo %$envVarName%")
    else -> binSh("echo \$$envVarName")
}

private fun echoLoop(n: Int, message: String) = when (OS.type) {
    // Weird hack on Windows just to trim the double quotes while keeping the command valid even with special chars.
    // This is an abuse of the /P flag of the set command.
    OS.Type.Windows -> cmd("for /l %x in (1, 1, $n) do @echo|(@set /P dummy=\"$message\" && echo.)")
    else -> binSh("for i in `seq 1 $n`; do echo '$message'; done")
}

private fun cmd(command: String) = listOf("cmd", "/c", command)

private fun assertZeroExitCode(result: ProcessResult) {
    assertEquals(0, result.exitCode, "Process terminated with non-zero exit code ${result.exitCode}, " +
        "\n stdOut = ${result.stdout}")
}
