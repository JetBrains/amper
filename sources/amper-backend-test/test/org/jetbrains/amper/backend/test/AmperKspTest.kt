/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals

class AmperKspTest : AmperIntegrationTestBase() {

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    private fun TestCollector.setupTestDataProject(
        testProjectName: String,
        programArgs: List<String> = emptyList(),
        copyToTemp: Boolean = false,
    ): CliContext = setupTestProject(
        testDataRoot.resolve(testProjectName),
        copyToTemp = copyToTemp,
        programArgs = programArgs,
    )

    @Test
    fun `ksp jvm autoservice`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-autoservice")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "service-impl", fragment = "jvm")

        val expectedGeneratedFiles = setOf(
            generatedFilesDir / "resources/ksp/META-INF/services/com.sample.service.MyService",
        )
        assertEquals(expectedGeneratedFiles, generatedFilesDir.walk().toSet())

        backend.runApplication()
        assertStdoutContains("Hello, service!")
    }

    @Test
    fun `ksp android room`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-android-room")
        val generatedSchemaPath = projectContext.projectRoot.path / "generated-db-schema"
        generatedSchemaPath.deleteRecursively()

        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-android-room", fragment = "android")

        val expectedGeneratedFiles = setOf(
            generatedFilesDir / "src/ksp/kotlin/com/jetbrains/sample/app/AppDatabase_Impl.kt",
            generatedFilesDir / "src/ksp/kotlin/com/jetbrains/sample/app/UserDao_Impl.kt",
        )
        assertEquals(expectedGeneratedFiles, generatedFilesDir.walk().toSet())

        val expectedGeneratedProjectFiles = setOf(
            generatedSchemaPath / "com.jetbrains.sample.app.AppDatabase/1.json",
        )
        assertEquals(expectedGeneratedProjectFiles, generatedSchemaPath.walk().toSet())
    }

    private fun CliContext.generatedFilesDir(module: String, fragment: String): Path =
        buildOutputRoot.path / "generated" / module / fragment
}
