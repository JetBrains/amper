/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmperBackendCustomTasksTest : AmperIntegrationTestBase() {

    private val testDataRoot: Path = TestUtil.amperTestProjectsRoot / "customTasks"

    private suspend fun TestCollector.setupTestDataProject(
        testProjectName: String,
        programArgs: List<String> = emptyList(),
        copyToTemp: Boolean = false,
    ): CliContext = setupTestProject(
        testProjectPath = testDataRoot.resolve(testProjectName),
        copyToTemp = copyToTemp,
        programArgs = programArgs,
    )

    @Test
    fun `generate versions file`() = runTestWithCollector {
        val projectContext = setupTestDataProject("generate-versions-file")
        AmperBackend(projectContext).runTask(TaskName(":generate-versions-file:runJvm"))
        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "VERSION=1.0-FANCY"
        )
    }

    @Test
    fun `generate resources`() = runTestWithCollector {
        val projectContext = setupTestDataProject("generate-resources")
        val backend = AmperBackend(projectContext)
        backend.showTasks()
        backend.runTask(TaskName(":generate-resources:runJvm"))
        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Resources OK"
        )
    }

    @Test
    fun `generate dist`() = runTestWithCollector {
        val projectContext = setupTestDataProject("generate-dist")
        val taskName = TaskName(":generate-dist:dist")
        AmperBackend(projectContext).runTask(taskName)
        val output = projectContext.getTaskOutputPath(taskName)
        val distFile = output.path.resolve("dist.zip")
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
    fun `generate artifact for publishing`() = runTestWithCollector {
        val groupDir = TestUtil.m2repository.resolve("amper/test/generate-artifact-for-publishing")
        groupDir.deleteRecursively()

        val projectContext = setupTestDataProject("generate-artifact-for-publishing")
        AmperBackend(projectContext).runTask(TaskName(":generate-artifact-for-publishing:publishJvmToMavenLocal"))

        val dir = groupDir.resolve("cli/1.0-FANCY")
        assertEquals("DIST ZIP", dir.resolve("cli-1.0-FANCY-dist.zip").readText())

        // pom is not expected to be generated
        assertFalse(dir.resolve("cli-1.0-FANCY-dist.pom").exists())
        assertFalse(dir.resolve("cli-1.0-FANCY.pom").exists())

        // cleanup
        groupDir.deleteRecursively()
    }
}
