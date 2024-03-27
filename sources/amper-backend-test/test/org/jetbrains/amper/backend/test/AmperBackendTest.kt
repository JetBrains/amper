/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.amper.backend.test

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.backend.test.assertions.spansNamed
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.test.TestUtil
import org.tinylog.Level
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmperBackendTest : IntegrationTestBase() {

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    private fun setupTestDataProject(testProjectName: String, programArgs: List<String> = emptyList()): ProjectContext =
        setupTestProject(testDataRoot.resolve(testProjectName), copyToTemp = false, programArgs = programArgs)

    @Test
    fun `jvm kotlin-test smoke test`() = runTestInfinitely {
        val projectContext = setupTestDataProject("jvm-kotlin-test-smoke")
        AmperBackend(projectContext).runTask(TaskName(":jvm-kotlin-test-smoke:testJvm"))

        val testLauncherSpan = openTelemetryCollector.spansNamed("junit-platform-console-standalone").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        // not captured by default...
        assertTrue(stdout.contains("Hello from test method, JavaString"), stdout)

        assertTrue(stdout.contains("[         1 tests successful      ]"), stdout)
        assertTrue(stdout.contains("[         0 tests failed          ]"), stdout)

        val xmlReport = projectContext.buildOutputRoot.path.resolve("tasks/_jvm-kotlin-test-smoke_testJvm/reports/TEST-junit-vintage.xml")
            .readText()

        assertTrue(xmlReport.contains("<testcase name=\"smoke\" classname=\"apkg.ATest\""), xmlReport)
    }

    @Test
    fun `jvm run tests only from test fragment`() = runTestInfinitely {
        // asserts that ATest.smoke is run, but SrcTest.smoke isn't

        val projectContext = setupTestDataProject("jvm-test-classpath")
        AmperBackend(projectContext).runTask(TaskName(":jvm-test-classpath:testJvm"))

        val testLauncherSpan = openTelemetryCollector.spansNamed("junit-platform-console-standalone").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[         1 tests successful      ]"), stdout)
        assertTrue(stdout.contains("[         0 tests failed          ]"), stdout)

        val xmlReport = projectContext.buildOutputRoot.path.resolve("tasks/_jvm-test-classpath_testJvm/reports/TEST-junit-jupiter.xml")
            .readText()

        assertTrue(xmlReport.contains("<testcase name=\"smoke()\" classname=\"apkg.ATest\""), xmlReport)
    }

    @Test
    fun `jvm jar task with main class`() = runTestInfinitely {
        val projectContext = setupTestDataProject("java-kotlin-mixed")
        AmperBackend(projectContext).runTask(TaskName(":java-kotlin-mixed:jarJvm"))

        val jarPath = projectContext.buildOutputRoot.path.resolve("tasks/_java-kotlin-mixed_jarJvm/java-kotlin-mixed-jvm.jar")
        assertTrue(jarPath.isRegularFile(), "${jarPath.pathString} should exist and be a file")

        JarFile(jarPath.toFile()).use { jar ->
            val mainClass = jar.manifest.mainAttributes[Attributes.Name.MAIN_CLASS] as? String
            assertNotNull(mainClass, "The ${Attributes.Name.MAIN_CLASS} attribute should be present")
            assertEquals("bpkg.MainKt", mainClass)

            val entryNames = jar.entries().asSequence().map { it.name }.toList()
            val expectedEntriesInOrder = listOf(
                "META-INF/MANIFEST.MF",
                "META-INF/main.kotlin_module",
                "apkg/AClass.class",
                "bpkg/BClass.class",
                "bpkg/MainKt.class",
            )
            assertEquals(expectedEntriesInOrder, entryNames)
        }
    }

    @Test
    fun `jvm kotlin serialization support without explicit dependency`() = runTestInfinitely {
        val projectContext = setupTestDataProject("kotlin-serialization-default")
        AmperBackend(projectContext).runTask(TaskName(":kotlin-serialization-default:runJvm"))

        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Hello, World!"
        )
    }

    @Test
    fun `get jvm resource from dependency`() = runTestInfinitely {
        val projectContext = setupTestDataProject("jvm-resources")
        AmperBackend(projectContext).runTask(TaskName(":two:runJvm"))

        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "String from resources: Stuff From Resources"
        )
    }

    @Test
    fun `jvm test fragment dependencies`() = runTestInfinitely {
        val projectContext = setupTestDataProject("jvm-test-fragment-dependencies")
        AmperBackend(projectContext).runTask(TaskName(":root:testJvm"))

        val testLauncherSpan = openTelemetryCollector.spansNamed("junit-platform-console-standalone").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("FromExternalDependencies:OneTwo FromProject:MyUtil"), stdout)
    }

    @Test
    fun `do not call kotlinc again if sources were not changed`() = runTestInfinitely {
        val projectContext = setupTestDataProject("language-version")

        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))
        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Hello, world!"
        )
        kotlinJvmCompilationSpans.assertSingle()

        resetCollectors()

        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))
        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)
        kotlinJvmCompilationSpans.assertNone()
    }

    @Test
    fun `kotlin compiler span`() = runTestInfinitely {
        val projectContext = setupTestDataProject("language-version")
        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)

        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
            hasAmperModule("language-version")
        }
        assertLogContains(text = "main.kt:1:10 Parameter 'args' is never used", level = Level.WARN)
    }

    @Test
    fun `mixed java kotlin`() = runTestInfinitely {
        val projectContext = setupTestDataProject("java-kotlin-mixed")
        AmperBackend(projectContext).runTask(TaskName(":java-kotlin-mixed:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Output: <XYZ>"
        assertInfoLogStartsWith(find)
    }

    @Test
    fun `simple multiplatform cli on jvm`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = argumentsWithSpecialChars)
        AmperBackend(projectContext).runTask(TaskName(":jvm-cli:runJvm"))

        val find = """Process exited with exit code 0
STDOUT:
Hello Multiplatform CLI 12: JVM World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        assertInfoLogStartsWith(find)
    }

    @Test
    fun `simple multiplatform cli should compile windows on any platform`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = argumentsWithSpecialChars)
        AmperBackend(projectContext).runTask(TaskName(":windows-cli:compileMingwX64"))

        assertTrue("build must generate a 'windows-cli.exe' file somewhere") {
            projectContext.buildOutputRoot.path.walk().any { it.name == "windows-cli.exe" }
        }
    }

    @Test
    fun `jvm exported dependencies`() = runTestInfinitely {
        val projectContext = setupTestDataProject("jvm-exported-dependencies")
        AmperBackend(projectContext).runTask(TaskName(":cli:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "From Root Module + OneTwo"
        assertInfoLogStartsWith(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli on mac`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = argumentsWithSpecialChars)
        AmperBackend(projectContext).runTask(TaskName(":macos-cli:runMacosArm64"))

        val find = """Process exited with exit code 0
STDOUT:
Hello Multiplatform CLI 12: Mac World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        assertInfoLogStartsWith(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli test on mac`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":shared:testMacosArm64"))

        val testLauncherSpan = openTelemetryCollector.spansNamed("native-test").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[       OK ] WorldTest.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @WindowsOnly
    fun `simple multiplatform cli test on windows`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":shared:testMingwX64"))

        val testLauncherSpan = openTelemetryCollector.spansNamed("native-test").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[       OK ] WorldTest.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @LinuxOnly
    fun `simple multiplatform cli on linux`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = argumentsWithSpecialChars)
        AmperBackend(projectContext).runTask(TaskName(":linux-cli:runLinuxX64"))

        val find = """Process exited with exit code 0
STDOUT:
Hello Multiplatform CLI 12: Linux World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        assertInfoLogStartsWith(find)
    }

    @Test
    @WindowsOnly
    fun `simple multiplatform cli on windows`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = argumentsWithSpecialChars)
        AmperBackend(projectContext).runTask(TaskName(":windows-cli:runMingwX64"))

        val find = """Process exited with exit code 0
STDOUT:
Hello Multiplatform CLI 12: Windows (Mingw) World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        assertInfoLogStartsWith(msgPrefix = find)
    }

    private val specialCmdChars = "&()[]{}^=;!'+,`~"
    private val argumentsWithSpecialChars = listOf(
        "simple123",
        "my arg2",
        "my arg3 :\"'<>\$ && || ; \"\" $specialCmdChars ${specialCmdChars.chunked(1).joinToString(" ")}",
    )

    @Test
    fun `simple multiplatform cli sources jars`() = runTestInfinitely {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = emptyList())
        val backend = AmperBackend(projectContext)

        val sourcesJarJvm = TaskName(":shared:sourcesJarJvm")
        backend.runTask(sourcesJarJvm)
        val sourcesJarLinuxArm64Task = TaskName(":shared:sourcesJarLinuxArm64")
        backend.runTask(sourcesJarLinuxArm64Task)
        val sourcesJarLinuxX64Task = TaskName(":shared:sourcesJarLinuxX64")
        backend.runTask(sourcesJarLinuxX64Task)
        val sourcesJarMingwX64Task = TaskName(":shared:sourcesJarMingwX64")
        backend.runTask(sourcesJarMingwX64Task)
        val sourcesJarMacosArm64Task = TaskName(":shared:sourcesJarMacosArm64")
        backend.runTask(sourcesJarMacosArm64Task)

        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarJvm) / "shared-jvm-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "jvmMain/Jvm.java",
                "jvmMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarLinuxArm64Task) / "shared-linuxarm64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "linuxMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarLinuxX64Task) / "shared-linuxx64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "linuxMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarMingwX64Task) / "shared-mingwx64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "mingwMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarMacosArm64Task) / "shared-macosarm64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "macosMain/World.kt",
            )
        )
    }
}

private fun assertJarFileEntries(jarPath: Path, expectedEntries: List<String>) {
    assertTrue(jarPath.existsCaseSensitive(), "Jar file $jarPath doesn't exist")
    JarFile(jarPath.toFile()).use { jar ->
        assertEquals(expectedEntries, jar.entries().asSequence().map { it.name }.toList())
    }
}

private fun Path.existsCaseSensitive(): Boolean =
    // toRealPath() ensures the case matches what's on disk even on Windows
    exists() && absolute().normalize().pathString == toRealPath(LinkOption.NOFOLLOW_LINKS).pathString

// This is not a public API, we're not supposed to know where the outputs are, but it's convenient to test
// if the task output is correct. We might remove it later when the output of the tested tasks are actually
// tested with their real-life usage. For instance, source jars will be tested as part of publication.
private fun ProjectContext.taskOutputPath(taskName: TaskName): Path =
    buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_")
