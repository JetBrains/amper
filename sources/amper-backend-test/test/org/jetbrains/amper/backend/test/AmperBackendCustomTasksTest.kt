/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertTrue

class AmperBackendCustomTasksTest : AmperIntegrationTestBase() {

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/customTasks")

    private fun setupTestDataProject(
        testProjectName: String,
        programArgs: List<String> = emptyList(),
        copyToTemp: Boolean = false,
        backgroundScope: CoroutineScope,
    ): ProjectContext = setupTestProject(
        testDataRoot.resolve(testProjectName),
        copyToTemp = copyToTemp,
        programArgs = programArgs,
        backgroundScope = backgroundScope,
    )

    @Test
    fun `generate versions file`() = runTestInfinitely {
        val projectContext = setupTestDataProject("generate-versions-file", backgroundScope = backgroundScope)
        AmperBackend(projectContext).runTask(TaskName(":generate-versions-file:runJvm"))
        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "VERSION=1.0-FANCY"
        )
    }

    @Test
    fun `generate dist`() = runTestInfinitely {
        val projectContext = setupTestDataProject("generate-dist", backgroundScope = backgroundScope)
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
}
