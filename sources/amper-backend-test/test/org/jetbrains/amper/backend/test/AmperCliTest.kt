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
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals

class AmperCliTest {
    @Test
    fun smoke() = runTestInfinitely {
        val result = runCli(testDataRoot.resolve("jvm-kotlin-test-smoke"), listOf("tasks"))
        assertEquals(0, result.exitCode)
        println(result.stdout.prependIndent("STDOUT ").trim())
        println(result.stderr.prependIndent("STDERR ").trim())
    }

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    private suspend fun runCli(projectRoot: Path, args: List<String>): ProcessResult {
        val jdk = JdkDownloader.getJdk(AmperUserCacheRoot(TestUtil.userCacheRoot))
        return jdk.runJava(
            workingDir = projectRoot,
            mainClass = "org.jetbrains.amper.cli.MainKt",
            classpath = classpath,
            programArgs = args,
            jvmArgs = listOf("-ea"),
        )
    }

    private val classpath: List<Path> by lazy {
        val unpackedLibRoot = TestUtil.amperCheckoutRoot.resolve("sources/cli/build/unpackedDistribution/lib")
        check(unpackedLibRoot.isDirectory()) {
            "Not a directory: $unpackedLibRoot"
        }
        val jars = unpackedLibRoot.listDirectoryEntries("*.jar")
        check(jars.isNotEmpty()) {
            "No jars at: $jars"
        }
        return@lazy jars.sorted()
    }
}
