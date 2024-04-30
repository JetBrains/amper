/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.backend.test.extensions.TempDirExtension
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AmperCliTestBase {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    protected val tempRoot: Path by lazy {
        // Always run tests in a directory with space, tests quoting in a lot of places
        // Since TempDirExtension generates temp directory under TestUtil.tempDir
        // it should already contain a space in the part
        // assert it anyway
        val path = tempDirExtension.path
        check(path.pathString.contains(" ")) {
            "Temp path should contain a space: ${path.pathString}"
        }
        check(path.isDirectory()) {
            "Temp path is not a directory: $path"
        }
        path
    }

    protected abstract val testDataRoot: Path

    protected suspend fun runCli(backendTestProjectName: String, vararg args: String, expectedExitCode: Int = 0, assertEmptyStdErr: Boolean = true): ProcessResult {
        val projectRoot = testDataRoot.resolve(backendTestProjectName)

        return runCli(
            projectRoot,
            *args,
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr
        )
    }

    protected suspend fun runCli(
        projectRoot: Path,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
    ): ProcessResult {
        println("Running Amper CLI with '${args.toList()}' on $projectRoot")

        // TODO This should be an exact SDK which is run by our wrapper.
        //  Probably even the wrapper itself
        val jdk = JdkDownloader.getJdk(AmperUserCacheRoot(TestUtil.userCacheRoot))
        val buildOutputRoot = tempRoot.resolve("build")

        val result = jdk.runJava(
            workingDir = projectRoot,
            mainClass = "org.jetbrains.amper.cli.MainKt",
            classpath = classpath,
            programArgs = listOf(
                "--build-output",
                buildOutputRoot.pathString,
            ) + args,
            jvmArgs = listOf(
                "-ea",
                "-javaagent:$kotlinxCoroutinesCore",
                "-javaagent:$byteBuddyAgent",
            ),
            printOutputToTerminal = null,
        )

        val stdout = result.stdout.fancyPrependIndent("STDOUT: ").ifEmpty { "STDOUT: <no-output>" }
        val stderr = result.stderr.fancyPrependIndent("STDERR: ").ifEmpty { "STDERR: <no-output>" }

        assertEquals(
            expectedExitCode,
            result.exitCode,
            message = "Expected exit code $expectedExitCode, but got ${result.exitCode}:\n$stderr\n$stdout"
        )

        // TODO also assert no ERRORs or WARNs in logs by default

        if (assertEmptyStdErr) {
            assertTrue(
                result.stderr.isBlank(),
                message = "stderr should be generally empty for CLI call, but got:\n$stderr"
            )
        }

        // TODO export to junit test reporter in the future
        println("Result of running Amper CLI with '${args.toList()}' on $projectRoot:\n$stdout")

        return result
    }

    private val byteBuddyAgent: Path by lazy {
        val glob = "byte-buddy-agent-*.jar"
        val byteBuddyAgent = libRoot.listDirectoryEntries(glob)
        check(byteBuddyAgent.isNotEmpty()) {
            "'$glob' is expected under $libRoot"
        }
        check(byteBuddyAgent.size == 1) {
            "Only one file matching '$glob' is expected under $libRoot, but got: ${byteBuddyAgent.joinToString()}"
        }
        return@lazy byteBuddyAgent.single()
    }

    private val kotlinxCoroutinesCore: Path by lazy {
        val glob = "kotlinx-coroutines-core-jvm-*.jar"
        val matched = libRoot.listDirectoryEntries(glob)
        check(matched.isNotEmpty()) {
            "'$glob' is expected under $libRoot"
        }
        check(matched.size == 1) {
            "Only one file matching '$glob' is expected under $libRoot, but got: ${matched.joinToString()}"
        }
        return@lazy matched.single()
    }

    private val libRoot: Path by lazy {
        val unpackedLibRoot = TestUtil.amperCheckoutRoot.resolve("sources/cli/build/unpackedDistribution/lib")
        check(unpackedLibRoot.isDirectory()) {
            "Not a directory: $unpackedLibRoot"
        }
        val jars = unpackedLibRoot.listDirectoryEntries("*.jar")
        check(jars.isNotEmpty()) {
            "No jars at: $jars"
        }
        return@lazy unpackedLibRoot
    }

    private val classpath: List<Path> by lazy {
        val jars = libRoot.listDirectoryEntries("*.jar")
        check(jars.isNotEmpty()) {
            "No jars at: $jars"
        }
        return@lazy jars.sorted()
    }

    private fun String.fancyPrependIndent(prepend: String): String {
        val trim = trim()
        if (trim.isEmpty()) return trim

        return trim.prependIndent(prepend).trim()
    }
}
