/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertJavaIncrementalCompilationState
import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperJavaBuildTest : AmperCliTestBase() {

    @Test
    fun `full java build`() = runSlowTest {
        val result = runWithJic(copyTestProject("java-single"), moduleName = "java-single")

        val expectedClassFiles = listOf(
            "apkg/JavaWorld.class",
            "apkg/Main.class",
            "apkg/Util.class",
            "apkg/World.class",
        )

        val actualClassFiles = result.getClassFilesFromTheTaskOutput("java-single")
        assertEquals(expectedClassFiles, actualClassFiles)

        assertEquals("Hello, Java World!", result.realStdout())
    }

    private fun AmperCliResult.getClassFilesFromTheTaskOutput(moduleName: String): List<String> {
        val buildOutput = buildOutputRoot /  "artifacts" / "CompiledJvmArtifact" / "${moduleName}jvm"
        val result = mutableListOf<String>()
        for (outputFolder in listOf("java-output", "kotlin-output", "resources-output")) {
            val folder = buildOutput / outputFolder
            result.addAll(folder.walk().toList().map { it.relativeTo(folder).invariantSeparatorsPathString })
        }
        return result.sorted()
    }

    @Test
    fun `compiler errors are reported`() = runSlowTest {
        val result = buildWithJic(
            copyTestProject("java-error"),
            expectedExitCode = null,
            assertEmptyStdErr = false
        )

        assertNotEquals(result.exitCode, 0, "Exit code should not be 0 because there are compiler errors")
        result.assertStderrContains("';' expected")
        result.assertStderrContains("java-error/src/apkg/Main.java")
    }

    @Test
    fun `incremental java build`() = runSlowTest {
        val projectRoot = copyTestProject("java-single")
        runWithJic(projectRoot, moduleName = "java-single")

        (projectRoot / "src" / "apkg" / "Main.java").replaceText("Hello", "Goodbye")

        val result2 = runWithJic(projectRoot, moduleName = "java-single")
        assertEquals("Goodbye, Java World!", result2.realStdout())
    }

    @Test
    fun `removed source file leads to removal of class in case of incremental compilation`() = runSlowTest {
        val projectRoot = copyTestProject("java-single")
        runWithJic(projectRoot, moduleName = "java-single")

        // remove 2 files
        val apkgDir = projectRoot / "src" / "apkg"
        (apkgDir / "JavaWorld.java").deleteExisting()
        (apkgDir / "World.java").deleteExisting()

        // change Main.java so that it doesn't depend on them
        val mainModifiedJava = projectRoot / "Main-modified-for-removal.java"
        assertTrue("The file $mainModifiedJava should exist", mainModifiedJava.exists())
        val mainOriginal = projectRoot / "src" / "apkg" / "Main.java"
        assertTrue("The file $mainOriginal should exist", mainOriginal.exists())
        mainModifiedJava.moveTo(mainOriginal, overwrite = true)

        val result2 = runWithJic(projectRoot, moduleName = "java-single")
        assertEquals("Hello, simple world!", result2.realStdout())

        // check that class files for removed source files are removed as well
        val actualClassFiles = result2.getClassFilesFromTheTaskOutput("java-single")
        assertEquals(listOf(
            "apkg/Main.class",
            "apkg/Util.class",
        ), actualClassFiles)
    }

    @Test
    fun `removed source file leads to removal of class in case of full rebuild`() = runSlowTest {
        val projectRoot = copyTestProject("java-single")
        runWithJic(projectRoot, moduleName = "java-single")

        // remove 2 files
        val apkgDir = projectRoot / "src" / "apkg"
        (apkgDir / "JavaWorld.java").deleteExisting()
        (apkgDir / "World.java").deleteExisting()

        // change Main.java so that it doesn't depend on them
        val mainModifiedJava = projectRoot / "Main-modified-for-removal.java"
        assertTrue("The file $mainModifiedJava should exist", mainModifiedJava.exists())
        val mainOriginal = projectRoot / "src" / "apkg" / "Main.java"
        assertTrue("The file $mainOriginal should exist", mainOriginal.exists())
        mainModifiedJava.moveTo(mainOriginal, overwrite = true)

        // also remove Util.java to make 100% of files change leading to the full rebuild
        (apkgDir / "Util.java").deleteExisting()

        val result2 = runWithJic(projectRoot, moduleName = "java-single")
        assertEquals("Hello, simple world!", result2.realStdout())

        // check that class files for removed source files are removed as well
        val actualClassFiles = result2.getClassFilesFromTheTaskOutput("java-single")
        assertEquals(listOf(
            "apkg/Main.class",
        ), actualClassFiles)
    }

    @Test
    fun `two-module build after changes in both modules`() = runSlowTest {
        val projectRoot = copyTestProject("java-two")
        runWithJic(projectRoot, moduleName = "main")

        (projectRoot / "main" / "src" / "com" / "Main.java").replaceText("Hello", "Goodbye")
        (projectRoot / "lib" / "src" / "org" / "JavaWorld.java").replaceText("<Java>", "Amper")

        val result2 = runWithJic(projectRoot, moduleName = "main")
        assertEquals("Goodbye, Amper World!", result2.realStdout())

        val actualClassFiles = result2.getClassFilesFromTheTaskOutput("main")
        val expected = listOf(
            "com/Main.class",
            "com/Util.class",
        )
        assertEquals(expected, actualClassFiles)

        val libClassFiles = result2.getClassFilesFromTheTaskOutput("lib")
        val libExpected = listOf(
            "org/JavaWorld.class",
            "org/Util.class",
            "org/World.class",
        )
        assertEquals(libExpected, libClassFiles)
    }

    @Test
    fun `three-module build after changes in both modules`() = runSlowTest {
        val projectRoot = copyTestProject("java-three")
        runWithJic(projectRoot, moduleName = "main")

        (projectRoot / "main" / "src" / "com" / "main" / "Main.java").replaceText("<Main>", "Main1")
        (projectRoot / "small" / "src" / "small" / "Small.java").replaceText("<Small>", "Small1")
        (projectRoot / "big" / "src" / "big" / "Big.java").replaceText("<Big>", "Big1")

        val result2 = runWithJic(projectRoot, moduleName = "main")
        assertEquals("Hello from Main1, Small1, Big1", result2.realStdout())
    }

    private fun Path.replaceText(oldText: String, newText: String) {
        val newJavaWorld = this.readText().replace(oldText, newText)
        this.writeText(newJavaWorld)
    }

    private fun getSourceProjectRoot(projectName: String): Path =
        Dirs.amperTestProjectsRoot / "java" / projectName

    private fun copyTestProject(projectName: String): Path {
        val projectRootFromSources = getSourceProjectRoot(projectName)
        val tempProjectDir = tempRoot / UUID.randomUUID().hashCode().toString() / projectRootFromSources.fileName
        tempProjectDir.createDirectories()
        projectRootFromSources.copyToRecursively(target = tempProjectDir, overwrite = false, followLinks = true)
        return tempProjectDir
    }

    private suspend fun runWithJic(projectRoot: Path, moduleName: String): AmperCliResult {
        val result = runCli(projectRoot = projectRoot, "run", "-m", moduleName)
        result.assertJavaIncrementalCompilationState(compileJavaIncrementally = true, moduleName)
        return result
    }

    private suspend fun buildWithJic(
        projectRoot: Path,
        expectedExitCode: Int? = 0,
        assertEmptyStdErr: Boolean = true,
        ): AmperCliResult =
        runCli(
            projectRoot = projectRoot,
            "build",
            expectedExitCode = expectedExitCode,
            assertEmptyStdErr = assertEmptyStdErr
        )

    private fun AmperCliResult.realStdout(): String {
        // remove the last line from the logger
        return stdoutClean.substringBefore("Process exited with exit code").substringBeforeLast("\n")
    }
}
