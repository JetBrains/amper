/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.assertStdoutDoesNotContain
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains

class MavenPluginsTest : AmperCliTestBase() {
    @Test
    fun `surefire plugin test goal exists as task`() = runSlowTest {
        runCli(
            projectDir = testProject("extensibility-maven/surefire-plugin"),
            "show", "tasks",
            copyToTempDir = true,
        ).assertStdoutContains("maven-surefire-plugin.test")
    }

    @Test
    fun `no maven tasks appear on the task list if no maven plugins are specified`() = runSlowTest {
        runCli(
            // Just some project with java source code.
            projectDir = testProject("java-kotlin-mixed"),
            "show", "tasks",
            copyToTempDir = true,
        ).assertStdoutDoesNotContain("maven")
    }

    @Test
    fun `surefire plugin test goal executes as task`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin")
            .assertStdoutContains("Hello from surefire execution")
    }

    @Test
    fun `surefire plugin test goal executes as task with dependency between modules`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin-multi-module")
            .assertStdoutContains("Hello from surefire execution")
    }

    @Test
    fun `surefire plugin test goal executes with junit assertion and test fails`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin-junit-assertion", expectedExitCode = 1)
            .assertStderrContains("expected: <foo> but was: <bar>")
    }

    @Test
    fun `surefire plugin test goal executes with junit filter and skips one test`() = runSlowTest {
        `run app-maven-surefire-plugin-test task`("surefire-plugin-two-tests-with-filter").apply {
            assertStdoutContains("Hello from firstTest")
            assertStdoutDoesNotContain("Hello from secondTest")
        }
    }

    @Test
    fun `surefire with jacoco test goal executes with argLine passing`() = runSlowTest {
        val result = runTask(
            projectWithMavenPath = "surefire-plugin-with-jacoco",
            taskName = "jacoco-maven-plugin.report"
        )

        result.assertStdoutContains("Hello from surefire execution")
        val mavenBuildDir = result.buildDir / "maven-target"
        assertExists(mavenBuildDir / "jacoco.exec")
        assertExists(mavenBuildDir / "reports" / "jacoco" / "index.html")
    }

    @Test
    fun `only jacoco executes with argLine passing to amper tests`() = runSlowTest {
        val result = runTask(
            projectWithMavenPath = "only-jacoco",
            taskName = "jacoco-maven-plugin.report"
        )

        result.assertStdoutContains("Hello from Amper test")
        val mavenBuildDir = result.buildDir / "maven-target"
        assertExists(mavenBuildDir / "jacoco.exec")
        assertExists(mavenBuildDir / "reports" / "jacoco" / "index.html")
    }

    @Test
    @DisabledOnOs(
        OS.WINDOWS,
        disabledReason = "Need to support long executable paths or shorten them for Win: " +
                "https://youtrack.jetbrains.com/issue/AMPER-4913/Long-executable-path-on-Win-leads-to-failure"
    )
    fun `protobuf maven plugin executes`() = runSlowTest {
        `run protobuf-maven-plugin-generate task`("protobuf-maven-plugin")
    }

    @Test
    @DisabledOnOs(
        OS.WINDOWS,
        disabledReason = "Need to support long executable paths or shorten them for Win: " +
                "https://youtrack.jetbrains.com/issue/AMPER-4913/Long-executable-path-on-Win-leads-to-failure"
    )
    fun `compilation and running with protobuf generated sources`() = runSlowTest {
        runCli(
            projectDir = testProject("extensibility-maven/protobuf-maven-plugin"),
            "run"
        ).assertStdoutContains("Hello from the proto test! Request value is 42")
    }

    @Test
    @DisabledOnOs(
        OS.WINDOWS,
        disabledReason = "Need to support long executable paths or shorten them for Win: " +
                "https://youtrack.jetbrains.com/issue/AMPER-4913/Long-executable-path-on-Win-leads-to-failure"
    )
    fun `compilation and testing with protobuf generated sources`() = runSlowTest {
        runCli(
            projectDir = testProject("extensibility-maven/protobuf-maven-plugin"),
            "test"
        ).assertStdoutContains("Hello from the proto test! Request value is 47")
    }

    @Test
    fun `checkstyle plugin performs a report`() = runSlowTest {
        val testProject = "checkstyle-plugin"
        val result = runTask(
            projectWithMavenPath = testProject,
            taskName = "maven-checkstyle-plugin.checkstyle",
        )

        val mavenBuildDir = result.buildDir / "maven-target"
        assertExists(mavenBuildDir / "reports" / "checkstyle.html")

        val checkstyleResult = mavenBuildDir / "checkstyle-result.xml"
        assertExists(checkstyleResult)

        val checkstyleResultText = checkstyleResult.readText()
        val pathToJavaFile = emptyPath / testProject / "app" / "src" / "Foo.java"
        assertContains(checkstyleResultText, pathToJavaFile.toString())
        assertContains(checkstyleResultText, "File does not end with a newline.")
        assertContains(checkstyleResultText, "Missing package-info.java file.")
    }

    @Test
    fun `checkstyle plugin with nohttp dependency performs a report`() = runSlowTest {
        val testProject = "checkstyle-plugin-with-dependency"
        val result = runTask(
            projectWithMavenPath = testProject,
            taskName = "maven-checkstyle-plugin.checkstyle",
        )

        val checkstyleResult = result.buildDir / "maven-target" / "checkstyle-result.xml"
        assertExists(checkstyleResult)
        val checkstyleResultText = checkstyleResult.readText()
        val pathToJavaFile = emptyPath / testProject / "app" / "src" / "dummy.txt"
        assertContains(checkstyleResultText, pathToJavaFile.toString())
        assertContains(checkstyleResultText, "http:// URLs are not allowed but got &apos;http://not.allowed.com&apos;")
    }

    @Test
    fun `maven enforcer plugin invalid XML is validated`() = runSlowTest {
        val testProject = "enforce-plugin-invalid-rules"
        val result = runTask(
            projectWithMavenPath = testProject,
            taskName = "maven-enforcer-plugin.enforce",
            expectedExitCode = 1,
        )
        result.assertStderrContains("app${File.separator}module.yaml:8:14: Expected a valid XML object for `PlexusConfiguration`, but the provided value is not valid XML")
    }
    
    @Test
    fun `maven enforcer plugin always fail rule`() = runSlowTest {
        val testProject = "enforce-plugin"
        val result = runTask(
            projectWithMavenPath = testProject,
            taskName = "maven-enforcer-plugin.enforce",
            expectedExitCode = 1,
        )
        result.assertStderrContains("This rule should fail")
    }

    private fun assertExists(path: Path) = assertTrue(path.exists()) { 
        val existingParent = generateSequence(path) { it.parent }.first { it.exists() }
        "Expected \"$path\" was not found. The first existing parent is: \"${existingParent.parent}\"." +
                "\nIts children are: ${existingParent.listDirectoryEntries().joinToString { "\"${it.name}\"" }}."
    }

    /**
     * An empty path for convenient construction with [Path.div]
     */
    private val emptyPath = Path("")

    /**
     * Run `:app:maven-surefire-plugin.test` task for the specified project from `extensibility-maven` directory.
     */
    private suspend fun `run app-maven-surefire-plugin-test task`(projectPath: String, expectedExitCode: Int = 0) =
        runTask(projectPath, "maven-surefire-plugin.test", expectedExitCode)

    /**
     * Run `:app:maven-surefire-plugin.test` task for the specified project from `extensibility-maven` directory.
     */
    private suspend fun `run protobuf-maven-plugin-generate task`(projectPath: String, expectedExitCode: Int = 0) =
        runTask(projectPath, "protobuf-maven-plugin.generate", expectedExitCode)

    private suspend fun runTask(
        projectWithMavenPath: String,
        taskName: String,
        expectedExitCode: Int = 0,
    ) = runCli(
        projectDir = testProject("extensibility-maven/$projectWithMavenPath"),
        "task", ":app:$taskName",
        copyToTempDir = true,
        expectedExitCode = expectedExitCode,
        assertEmptyStdErr = expectedExitCode == 0,
    )
}