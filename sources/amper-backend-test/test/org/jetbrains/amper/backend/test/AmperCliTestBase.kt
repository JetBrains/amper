/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
abstract class AmperCliTestBase {

    protected abstract val testDataRoot: Path

    protected suspend fun runCli(backendTestProjectName: String, args: List<String>, expectedExitCode: Int = 0, assertEmptyStdErr: Boolean = true): ProcessResult {
        val projectRoot = testDataRoot.resolve(backendTestProjectName)
        // TODO This should be an exact SDK which is run by our wrapper.
        //  Probably even the wrapper itself

        val jdk = JdkDownloader.getJdk(AmperUserCacheRoot(TestUtil.userCacheRoot))

        println("Running Amper CLI with '$args' on $projectRoot")

        val result = jdk.runJava(
            workingDir = projectRoot,
            mainClass = "org.jetbrains.amper.cli.MainKt",
            classpath = classpath,
            programArgs = args,
            jvmArgs = listOf(
                "-ea",
                "-javaagent:$kotlinxCoroutinesCore",
                "-javaagent:$byteBuddyAgent",
            ),
            printOutput = false,
        )

        val stdout = result.stdout.fancyPrependIndent("STDOUT: ").ifEmpty { "STDOUT: <no-output>" }
        val stderr = result.stderr.fancyPrependIndent("STDERR: ").ifEmpty { "STDERR: <no-output>" }

        assertEquals(expectedExitCode, result.exitCode, message = "Expected exit code $expectedExitCode, but got ${result.exitCode}:\n$stderr\n$stdout")

        // TODO also assert no ERRORs or WARNs in logs by default

        if (assertEmptyStdErr) {
            assertTrue(
                result.stderr.isBlank(),
                message = "stderr should be generally empty for CLI call, but got:\n$stderr"
            )
        }

        // TODO export to junit test reporter in the future
        println("Result of running Amper CLI with '$args' on $projectRoot:\n$stdout")

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
