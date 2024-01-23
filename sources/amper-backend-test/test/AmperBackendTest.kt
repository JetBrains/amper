/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.engine.TaskName
import org.tinylog.Level
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AmperBackendTest : IntegrationTestBase() {

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    private fun setupTestDataProject(testProjectName: String): ProjectContext =
        setupTestProject(testDataRoot.resolve(testProjectName), copyToTemp = false)

    @Test
    fun `jvm kotlin-test smoke test`() {
        val projectContext = setupTestDataProject("jvm-kotlin-test-smoke")
        AmperBackend(projectContext).runTask(TaskName(":jvm-kotlin-test-smoke:testJvm"))

        val testLauncherSpan = openTelemetryCollector.spans.single { it.name == "junit-platform-console-standalone" }
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        // not captured by default...
        assertTrue(stdout.contains("Hello from test method"), stdout)

        assertTrue(stdout.contains("[         1 tests successful      ]"), stdout)
        assertTrue(stdout.contains("[         0 tests failed          ]"), stdout)

        val xmlReport = projectContext.buildOutputRoot.path.resolve("tasks/_jvm-kotlin-test-smoke_testJvm/reports/TEST-junit-jupiter.xml")
            .readText()

        assertTrue(xmlReport.contains("<testcase name=\"smoke()\" classname=\"apkg.ATest\""), xmlReport)
    }

    @Test
    fun `get jvm resource from dependency`() {
        val projectContext = setupTestDataProject("jvm-resources")
        AmperBackend(projectContext).runTask(TaskName(":two:runJvm"))

        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "String from resources: Stuff From Resources"
        )
    }

    @Test
    fun `do not call kotlinc again if sources were not changed`() {
        val projectContext = setupTestDataProject("language-version")

        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))
        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Hello, world!"
        )
        assertEquals(1, kotlinJvmCompilerSpans.size)

        openTelemetryCollector.reset()
        logCollector.reset()

        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))
        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)
        assertEquals(0, kotlinJvmCompilerSpans.size)
    }

    @Test
    fun `kotlin compiler span`() {
        val projectContext = setupTestDataProject("language-version")
        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)

        val compilationSpan = kotlinJvmCompilerSpans.singleOrNull() ?: fail("No kotlin compilation span (or more than 1)")
        compilationSpan.assertKotlinCompilerArgument("-language-version", "1.9")

        assertLogContains(text = "main.kt:1:10 Parameter 'args' is never used", level = Level.WARN)

        val amperModuleAttr = compilationSpan.getAttribute(AttributeKey.stringKey("amper-module"))
        assertEquals("language-version", amperModuleAttr)
    }

    @Test
    fun `mixed java kotlin`() {
        val projectContext = setupTestDataProject("java-kotlin-mixed")
        AmperBackend(projectContext).runTask(TaskName(":java-kotlin-mixed:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Output: <XYZ>"
        assertInfoLogStartsWith(find)
    }

    @Test
    fun `simple multiplatform cli on jvm`() {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":jvm-cli:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: JVM World"
        assertInfoLogStartsWith(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli on mac`() {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":macos-cli:runMacosArm64"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: Mac World"
        assertInfoLogStartsWith(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli test on mac`() {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":shared:testMacosArm64"))

        val testLauncherSpan = openTelemetryCollector.spans.single { it.name == "native-test" }
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[       OK ] WorldTest.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @LinuxOnly
    fun `simple multiplatform cli on linux`() {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":linux-cli:runLinuxX64"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: Linux World"
        assertInfoLogStartsWith(msgPrefix = find)
    }
}
