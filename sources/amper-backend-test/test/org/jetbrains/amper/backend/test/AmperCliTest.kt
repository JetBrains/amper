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
import kotlin.io.path.deleteRecursively
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class AmperCliTest {
    @Test
    fun smoke() = runTestInfinitely {
        runCli("jvm-kotlin-test-smoke", listOf("tasks"))
    }

    @Test
    fun publish() = runTestInfinitely {
        val m2repository = Path.of(System.getProperty("user.home"), ".m2/repository")
        val groupDir = m2repository.resolve("amper").resolve("test")
        groupDir.deleteRecursively()

        runCli("jvm-publish", listOf("publish", "mavenLocal"))

        val files = groupDir.walk()
            .onEach {
                check(it.fileSize() > 0) { "File should not be empty: $it" }
            }
            .map { it.relativeTo(groupDir).pathString.replace('\\', '/') }
            .sorted()
        assertEquals(
            """
                artifactName/2.2/_remote.repositories
                artifactName/2.2/artifactName-2.2-sources.jar
                artifactName/2.2/artifactName-2.2.jar
                artifactName/2.2/artifactName-2.2.pom
                artifactName/maven-metadata-local.xml
            """.trimIndent(), files.joinToString("\n")
        )
    }

    private fun String.fancyPrependIndent(prepend: String): String {
        val trim = trim()
        if (trim.isEmpty()) return trim

        return trim.prependIndent(prepend).trim()
    }

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    private suspend fun runCli(backendTestProjectName: String, args: List<String>, expectedExitCode: Int = 0, assertEmptyStdErr: Boolean = true): ProcessResult {
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
            printOutput = true,
        )

        val stdout = result.stdout.fancyPrependIndent("STDOUT: ").ifEmpty { "STDOUT: <no-output>" }
        val stderr = result.stderr.fancyPrependIndent("STDERR: ").ifEmpty { "STDERR: <no-output>" }

        assertEquals(expectedExitCode, result.exitCode, message = "Expected exit code $expectedExitCode, but got ${result.exitCode}:\n$stderr\n$stdout")

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
}
