/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import java.util.zip.ZipFile
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomTasksTest : AmperCliTestBase() {

    @Test
    fun `generate versions file`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("customTasks/generate-versions-file"),
            "run",
            "--module=generate-versions-file",
        )
        result.assertStdoutContains(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "VERSION=1.0-FANCY"
        )
    }

    @Test
    fun `generate resources`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("customTasks/generate-resources"),
            "run",
            "--module=generate-resources",
        )
        result.assertStdoutContains(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Resources OK"
        )
    }

    @Test
    fun `generate dist`() = runSlowTest {
        val taskName = ":generate-dist:dist"
        val result = runCli(projectRoot = testProject("customTasks/generate-dist"), "task", taskName)
        val output = result.getTaskOutputPath(taskName)
        val distFile = output.resolve("dist.zip")
        assertTrue(distFile.isRegularFile())
        val names = ZipFile(distFile.toFile()).use { zip -> zip.entries().asSequence().toList().map { it.name } }
        fun assertHavingFileByPrefix(prefix: String) {
            assertTrue(names.singleOrNull { it.startsWith(prefix) } != null,
                "Zip output must contain one and only file named '$prefix*': [${names.joinToString(", ")}]")
        }

        assertHavingFileByPrefix("generate-dist-jvm.jar")
        assertHavingFileByPrefix("kotlin-stdlib-")
        assertHavingFileByPrefix("kotlinx-datetime-jvm-")
    }

    @Test
    fun `generate artifact for publishing`() = runSlowTest {
        val mavenLocalForTest = tempRoot.resolve(".m2.test").also { it.createDirectories() }
        val groupDir = mavenLocalForTest.resolve("amper/test/generate-artifact-for-publishing")

        runCli(
            projectRoot = testProject("customTasks/generate-artifact-for-publishing"),
            "publish", "mavenLocal",
            amperJvmArgs = listOf("-Dmaven.repo.local=\"${mavenLocalForTest.absolutePathString()}\""),
        )

        val dir = groupDir.resolve("cli/1.0-FANCY")
        assertEquals("DIST ZIP", dir.resolve("cli-1.0-FANCY-dist.zip").readText())

        // pom is not expected to be generated
        assertFalse(dir.resolve("cli-1.0-FANCY-dist.pom").exists())
        assertFalse(dir.resolve("cli-1.0-FANCY.pom").exists())
    }

    @Test
    fun `custom task dependencies`() = runSlowTest {
        val result = runCli(projectRoot = testProject("customTasks/custom-task-dependencies"), "show", "tasks")
        result.assertStdoutContains("task :main-lib:publishJvmToMavenLocal -> :main-lib:jarJvm, :main-lib:resolveDependenciesJvm, :main-lib:sourcesJarJvm, :utils:testJvm, :main-lib:testJvm")
    }
}
