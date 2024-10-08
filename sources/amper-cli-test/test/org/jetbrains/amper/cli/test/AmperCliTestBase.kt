/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.TestReporterExtension
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.androidHome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.RegisterExtension
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AmperCliTestBase {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    @RegisterExtension
    private val testReporter = TestReporterExtension()

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

    protected lateinit var testInfo: TestInfo
    protected val currentTestName: String
        get() = testInfo.testMethod.get().name

    @BeforeEach
    fun before(testInfo: TestInfo) {
        this.testInfo = testInfo
    }

    protected abstract val testDataRoot: Path

    protected suspend fun runCli(
        backendTestProjectName: String,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        stdin: ProcessInput? = null,
    ): ProcessResult {
        val projectRoot = testDataRoot.resolve(backendTestProjectName)
        check(projectRoot.isDirectory()) {
            "Project root is not a directory: $projectRoot"
        }

        return runCli(
            projectRoot,
            *args,
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr,
            stdin = stdin,
        )
    }

    protected suspend fun runCli(
        projectRoot: Path,
        vararg args: String,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        stdin: ProcessInput? = null,
    ): ProcessResult {
        println("Running Amper CLI with '${args.toList()}' on $projectRoot")

        // TODO This should be an exact SDK which is run by our wrapper.
        //  Probably even the wrapper itself
        val jdk = JdkDownloader.getJdk(AmperUserCacheRoot(TestUtil.userCacheRoot))
        val buildOutputRoot = tempRoot.resolve("build")
        val isDebuggingTest = ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:") }

        val result = jdk.runJava(
            workingDir = projectRoot,
            mainClass = "org.jetbrains.amper.cli.MainKt",
            classpath = classpath,
            programArgs = listOf(
                "--build-output",
                buildOutputRoot.pathString,
            ) + args,
            jvmArgs = buildList {
                add("-ea")
                add("-javaagent:$kotlinxCoroutinesCore")
                add("-javaagent:$byteBuddyAgent")

                // When debugging tests, we run the Amper CLI with jdwp to be able to attach a debugger to it.
                // The CLI process will wait for a debugger to attach on a dynamic port. You can click "Attach debugger"
                // in the IDEA console to automatically launch and attach a remote debugger.
                if (isDebuggingTest) {
                    add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y")
                }
            },
            environment = mapOf("ANDROID_HOME" to androidHome.pathString),
            outputListener = object : ProcessOutputListener {
                override fun onStdoutLine(line: String) {
                    if ("Listening for transport dt_socket" in line) {
                        // critical to see that the process stopped and to attach debugger from console output in IDEA
                        println(line)
                    }
                }
                override fun onStderrLine(line: String) = Unit
            },
            tempRoot = AmperProjectTempRoot(tempRoot),
            input = stdin,
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

        val message = "Result of running Amper CLI with '${args.toList()}' on $projectRoot:\n$stdout\n$stderr"

        println(message)
        // it should be enough to publish to junit reporter, but IDEA runner does not pick it up
        // if tests were run from Gradle
        testReporter.publishEntry(message)

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
        val distRoot = System.getProperty("amper.unpacked.dist.root")?.let { Path(it) }
            ?: run {
                // TODO this should be passed via system properties set in test settings
                // ref https://youtrack.jetbrains.com/issue/AMPER-253/Design-custom-tasks#focus=Comments-27-9984817.0-0
                TestUtil.amperCheckoutRoot.resolve("build/tasks/_cli_unpackedDist/dist")
            }

        val unpackedLibRoot = distRoot.resolve("lib")

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
