/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.relativeTo
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
        generatedFilesDir.assertContainsRelativeFiles(
            "resources/ksp/META-INF/services/com.sample.service.MyService",
        )

        backend.runApplication()
        assertStdoutContains("Hello, service!")
    }

    @Test
    fun `ksp jvm local processor`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-local-processor")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "consumer", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "resources/ksp/com/sample/generated/annotated-classes.txt",
        )

        backend.runApplication()
        assertStdoutTextContains("""
            My annotated classes are:
            org.sample.ksp.localprocessor.consumer.B
            org.sample.ksp.localprocessor.consumer.A
        """.trimIndent())
    }

    @Test
    fun `ksp jvm dagger`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-dagger")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-jvm-dagger", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/java/com/sample/dagger/CoffeeMaker_Factory.java",
            "src/ksp/java/com/sample/dagger/DaggerCoffeeShop.java",
            "src/ksp/java/com/sample/dagger/HeaterModule_Companion_ProvideHeaterFactory.java",
            "src/ksp/java/com/sample/dagger/Heater_Factory.java",
        )

        backend.runApplication()
        assertStdoutContains("Heater: heating...")
        assertStdoutContains("CoffeeMaker: brewing...")
    }

    @Test
    fun `ksp jvm dagger with catalog refs`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-dagger-catalog")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-jvm-dagger-catalog", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/java/com/sample/dagger/CoffeeMaker_Factory.java",
            "src/ksp/java/com/sample/dagger/DaggerCoffeeShop.java",
            "src/ksp/java/com/sample/dagger/HeaterModule_Companion_ProvideHeaterFactory.java",
            "src/ksp/java/com/sample/dagger/Heater_Factory.java",
        )

        backend.runApplication()
        assertStdoutContains("Heater: heating...")
        assertStdoutContains("CoffeeMaker: brewing...")
    }

    @Test
    fun `ksp android room`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-android-room")
        val generatedSchemaPath = projectContext.projectRoot.path / "generated-db-schema"
        generatedSchemaPath.deleteRecursively()

        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-android-room", fragment = "android")

        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/kotlin/com/jetbrains/sample/app/AppDatabase_Impl.kt",
            "src/ksp/kotlin/com/jetbrains/sample/app/UserDao_Impl.kt",
        )
        generatedSchemaPath.assertContainsRelativeFiles(
            "com.jetbrains.sample.app.AppDatabase/1.json",
        )
    }

    private fun CliContext.generatedFilesDir(module: String, fragment: String): Path =
        buildOutputRoot.path / "generated" / module / fragment
}

/**
 * Asserts that the directory at this [Path] contains all the files at the given [expectedRelativePaths].
 */
private fun Path.assertContainsRelativeFiles(vararg expectedRelativePaths: String) {
    val actualFiles = walk().map { it.relativeTo(this) }.toSet()
    val expectedFiles = expectedRelativePaths.map { Path(it) }.toSet()
    assertEquals(expectedFiles, actualFiles)
}
