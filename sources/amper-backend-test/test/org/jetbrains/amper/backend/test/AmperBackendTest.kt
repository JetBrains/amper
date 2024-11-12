/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import com.sun.net.httpserver.Authenticator
import com.sun.net.httpserver.BasicAuthenticator
import com.sun.net.httpserver.HttpServer
import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.backend.test.extensions.ErrorCollectorExtension
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.core.*
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.test.LinuxOnly
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.WindowsOnly
import org.jetbrains.amper.test.spans.assertEachKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.assertEachKotlinNativeCompilationSpan
import org.jetbrains.amper.test.spans.assertHasAttribute
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.kotlinJvmCompilationSpans
import org.jetbrains.amper.test.spans.spansNamed
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.RegisterExtension
import org.tinylog.Level
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmperBackendTest : AmperIntegrationTestBase() {

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    @RegisterExtension
    private val errorCollectorExtension = ErrorCollectorExtension()

    private suspend fun TestCollector.setupTestDataProject(
        testProjectName: String,
        programArgs: List<String> = emptyList(),
        copyToTemp: Boolean = false,
    ): CliContext = setupTestProject(
        testDataRoot.resolve(testProjectName),
        copyToTemp = copyToTemp,
        programArgs = programArgs,
    )

    @Test
    fun `jvm kotlin-test smoke test`() = runTestWithCollector {
        val projectContext = setupTestDataProject(
            "jvm-kotlin-test-smoke",
        )
        AmperBackend(projectContext).runTask(TaskName(":jvm-kotlin-test-smoke:testJvm"))

        val testLauncherSpan = spansNamed("junit-platform-console-standalone").assertSingle()
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
    fun `jvm test custom engine`() = runTestWithCollector {
        val projectContext = setupTestDataProject(
            "jvm-test-custom-engine",
        )
        AmperBackend(projectContext).test()

        // tests are discovered
        assertStdoutContains("my-test-1")
        assertStdoutContains("my-test-2")
    }

    @Test
    fun `jvm exclude test module`() = runTestWithCollector {
        val projectContext = setupTestDataProject(
            "jvm-multimodule-tests",
        )
        val backend = AmperBackend(projectContext)
        backend.test()

        // all tests run
        assertStdoutContains("Hello from test 1")
        assertStdoutContains("Hello from test 2")

        clearTerminalRecording()

        // tests from module 1 aren't run
        backend.test(excludeModules = setOf("one"))
        assertStdoutDoesNotContain("Hello from test 1")
        assertStdoutContains("Hello from test 2")
    }

    @Test
    fun `jvm kotlin test no tests`() = runTestWithCollector {
        // Testing a module should fail if there are some test sources, but no tests were found
        // for example it'll automatically fail if you run your tests with TestNG, but specified JUnit in settings
        // see `native test no tests`

        val projectContext = setupTestDataProject("jvm-kotlin-test-no-tests")
        val exception = assertFailsWith<UserReadableError> {
            AmperBackend(projectContext).test()
        }
        assertEquals("Task ':jvm-kotlin-test-no-tests:testJvm' failed: JVM tests failed for module 'jvm-kotlin-test-no-tests' with exit code 2 (no tests were discovered) (see errors above)", exception.message)
    }

    @Test
    @MacOnly
    fun `missing platform to test`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-kotlin-test-no-tests")
        val exception = assertFailsWith<UserReadableError> {
            AmperBackend(projectContext).test(
                requestedPlatforms = setOf(Platform.IOS_SIMULATOR_ARM64),
            )
        }
        assertEquals("No test tasks were found for platforms: IOS_SIMULATOR_ARM64", exception.message)
    }

    @Test
    @MacOnly
    fun `unsupported platform to test`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        val exception = assertFailsWith<UserReadableError> {
            AmperBackend(projectContext).test(
                requestedPlatforms = setOf(Platform.MINGW_X64),
            )
        }
        assertEquals("""
            Unable to run requested platform(s) on the current system.

            Requested unsupported platforms: mingwX64
            Runnable platforms on the current system: android iosSimulatorArm64 jvm macosArm64 tvosSimulatorArm64 watchosSimulatorArm64
        """.trimIndent(), exception.message)
    }

    @Test
    @WindowsOnly
    @Ignore("AMPER-474")
    fun `native test no tests`() = runTestWithCollector {
        // Testing a module should fail if there are some test sources, but no tests were found
        // see `jvm kotlin test no tests`

        val projectContext = setupTestDataProject("native-test-no-tests")
        val exception = assertFailsWith<UserReadableError> {
            AmperBackend(projectContext).test()
        }
        assertEquals("Some message about `no tests were discovered`", exception.message)
    }

    @Test
    @WindowsOnly
    @Ignore("AMPER-475")
    fun `native test app test`() = runTestWithCollector {
        // Testing a module should fail if there are some test sources, but no tests were found
        // see `jvm kotlin test no tests`

        val projectContext = setupTestDataProject("native-test-app-test")
        AmperBackend(projectContext).test()
        // TODO assert that some test was actually run
    }

    @Test
    fun `jvm kotlin test no test sources`() = runTestWithCollector {
        // Testing a module should not fail if there are no test sources at all
        // but warn about it

        val projectContext = setupTestDataProject("jvm-kotlin-test-no-test-sources")
        AmperBackend(projectContext).runTask(TaskName(":jvm-kotlin-test-no-test-sources:testJvm"))
        assertLogStartsWith("No test classes, skipping test execution for module 'jvm-kotlin-test-no-test-sources'", Level.WARN)
    }

    @Test
    @WindowsOnly
    @Ignore("AMPER-476")
    fun `native test no test sources`() = runTestWithCollector {
        // Testing a module should not fail if there are no test sources at all
        // but warn about it

        val projectContext = setupTestDataProject("native-test-no-test-sources")
        val amperBackend = AmperBackend(projectContext)
        amperBackend.runTask(TaskName(":native-test-no-test-sources:testMingwX64"))
        assertLogStartsWith("No test classes, skipping test execution for module 'jvm-kotlin-test-no-test-sources'", Level.WARN)
    }

    @Test
    fun `jvm run tests only from test fragment`() = runTestWithCollector {
        // asserts that ATest.smoke is run, but SrcTest.smoke isn't

        val projectContext = setupTestDataProject("jvm-test-classpath")
        AmperBackend(projectContext).runTask(TaskName(":jvm-test-classpath:testJvm"))

        val testLauncherSpan = spansNamed("junit-platform-console-standalone").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[         1 tests successful      ]"), stdout)
        assertTrue(stdout.contains("[         0 tests failed          ]"), stdout)

        val xmlReport = projectContext.buildOutputRoot.path.resolve("tasks/_jvm-test-classpath_testJvm/reports/TEST-junit-jupiter.xml")
            .readText()

        assertTrue(xmlReport.contains("<testcase name=\"smoke()\" classname=\"apkg.ATest\""), xmlReport)
    }

    @Test
    fun `jvm jar task with main class`() = runTestWithCollector {
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
    fun `jvm kotlin serialization support without explicit dependency`() = runTestWithCollector {
        val projectContext = setupTestDataProject("kotlin-serialization-default")
        AmperBackend(projectContext).runTask(TaskName(":kotlin-serialization-default:runJvm"))

        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Hello, World!"
        )
    }

    @Test
    fun `jvm kotlin serialization support with custom version`() = runTestWithCollector {
        val projectContext = setupTestDataProject("kotlin-serialization-custom-version")
        AmperBackend(projectContext).runTask(TaskName(":kotlin-serialization-custom-version:resolveDependenciesJvm"))

        spansNamed("resolve-dependencies")
            .assertSingle()
            .assertHasAttribute(
                key = AttributeKey.stringArrayKey("dependencies"),
                value = listOf(
                    "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}",
                    "org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.5.1",
                    "org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1",
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1",
                ),
            )
    }

    @Test
    fun `get jvm resource from dependency`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-resources")
        AmperBackend(projectContext).runTask(TaskName(":two:runJvm"))

        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "String from resources: Stuff From Resources"
        )
    }

    @Test
    fun `jvm test fragment dependencies`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-test-fragment-dependencies")
        AmperBackend(projectContext).runTask(TaskName(":root:testJvm"))

        val testLauncherSpan = spansNamed("junit-platform-console-standalone").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("FromExternalDependencies:OneTwo FromProject:MyUtil"), stdout)
    }

    @Test
    fun `do not call kotlinc again if sources were not changed`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-language-version-1.9")

        AmperBackend(projectContext).runTask(TaskName(":jvm-language-version-1.9:runJvm"))

        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Hello, world!"
        )
        kotlinJvmCompilationSpans.assertSingle()

        clearSpans()
        clearLogEntries()

        AmperBackend(projectContext).runTask(TaskName(":jvm-language-version-1.9:runJvm"))
        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)
        kotlinJvmCompilationSpans.assertNone()
    }

    @Test
    fun `kotlin compiler span`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-language-version-1.9")
        AmperBackend(projectContext).runTask(TaskName(":jvm-language-version-1.9:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)

        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
            hasAmperModule("jvm-language-version-1.9")
        }
        assertLogContains(text = "main.kt:1:10 Parameter 'args' is never used", level = Level.WARN)
    }

    @Test
    fun `jvm kotlin language version 2_0`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-language-version-2.0")
        AmperBackend(projectContext).runTask(TaskName(":jvm-language-version-2.0:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)

        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "2.0")
            hasAmperModule("jvm-language-version-2.0")
        }
    }

    @Test
    fun `native kotlin language version 1_9 compile`() = runTestWithCollector {
        val projectContext = setupTestDataProject("native-language-version-1.9")
        AmperBackend(projectContext).build()

        assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
        }
    }

    @Test
    @WindowsOnly
    fun `native kotlin language version 1_9 app run`() = runTestWithCollector {
        val projectContext = setupTestDataProject("native-language-version-1.9")
        AmperBackend(projectContext).runTask(TaskName(":app:runMingwX64"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, native!"
        assertInfoLogStartsWith(find)

        assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
        }
    }

    @Test
    fun `native kotlin language version 2_0 compile`() = runTestWithCollector {
        val projectContext = setupTestDataProject("native-language-version-2.0")
        AmperBackend(projectContext).build()

        assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version", "2.0")
        }
    }

    @Test
    @WindowsOnly
    fun `native kotlin language version 2_0 app run`() = runTestWithCollector {
        val projectContext = setupTestDataProject("native-language-version-2.0")
        AmperBackend(projectContext).runTask(TaskName(":app:runMingwX64"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, native!"
        assertInfoLogStartsWith(find)

        assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version", "2.0")
        }
    }

    @Test
    fun `multiplatform kotlin language version 1_9`() = runTestWithCollector {
        val projectContext = setupTestDataProject("multiplatform-language-version-1.9")
        AmperBackend(projectContext).build()

        assertEachKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
        }
        assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
        }
    }

    @Test
    fun `multiplatform kotlin language version 2_0`() = runTestWithCollector {
        val projectContext = setupTestDataProject("multiplatform-language-version-2.0")
        AmperBackend(projectContext).build()

        assertEachKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "2.0")
        }
        assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-language-version", "2.0")
        }
    }

    @Test
    fun `mixed java kotlin`() = runTestWithCollector {
        val projectContext = setupTestDataProject("java-kotlin-mixed")
        AmperBackend(projectContext).runTask(TaskName(":java-kotlin-mixed:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Output: <XYZ>"
        assertInfoLogStartsWith(find)
    }

    @Test
    fun `simple multiplatform cli on jvm`() = runTestWithCollector {
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
    fun `simple multiplatform cli should compile windows on any platform`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).build(setOf(Platform.MINGW_X64))

        assertTrue("build must generate a 'windows-cli.exe' file somewhere") {
            projectContext.buildOutputRoot.path.walk().any { it.name == "windows-cli.exe" }
        }
    }

    @Test
    fun `jvm exported dependencies`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-exported-dependencies")
        AmperBackend(projectContext).runTask(TaskName(":cli:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "From Root Module + OneTwo"
        assertInfoLogStartsWith(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli on mac`() = runTestWithCollector {
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
    fun `simple multiplatform cli lib test on mac`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":shared:testMacosArm64"))

        val testLauncherSpan = spansNamed("native-test").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[       OK ] WorldTest.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli app test on mac`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        val amperBackend = AmperBackend(projectContext)
        amperBackend.runTask(TaskName(":macos-cli:testMacosArm64"))

        val testLauncherSpan = spansNamed("native-test").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[       OK ] WorldTestFromMacOsCli.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @WindowsOnly
    fun `simple multiplatform cli test on windows`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":shared:testMingwX64"))

        val testLauncherSpan = spansNamed("native-test").assertSingle()
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[       OK ] WorldTest.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @LinuxOnly
    fun `simple multiplatform cli on linux`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = argumentsWithSpecialChars)
        val backend = AmperBackend(projectContext)
        backend.runTask(TaskName(":linux-cli:runLinuxX64"))

        val find = """Process exited with exit code 0
STDOUT:
Hello Multiplatform CLI 12: Linux World
ARG0: <${argumentsWithSpecialChars[0]}>
ARG1: <${argumentsWithSpecialChars[1]}>
ARG2: <${argumentsWithSpecialChars[2]}>"""
        assertInfoLogStartsWith(find)
    }

    @Test
    fun `jvm publish to maven local`() = runTestWithCollector {
        val m2repository = Path(System.getProperty("user.home"), ".m2/repository")
        val groupDir = m2repository.resolve("amper").resolve("test")
        groupDir.deleteRecursively()

        val projectContext = setupTestDataProject("jvm-publish")
        val backend = AmperBackend(projectContext)
        backend.runTask(TaskName(":jvm-publish:publishJvmToMavenLocal"))

        val files = groupDir.walk()
            .onEach {
                check(it.fileSize() > 0) { "File should not be empty: $it" }
            }
            .map { it.relativeTo(groupDir).pathString.replace('\\', '/') }
            .sorted()
        assertEquals(
            """
                artifactName/2.2/_remote.repositories
                artifactName/2.2/artifactName-2.2-sources.jar
                artifactName/2.2/artifactName-2.2.jar
                artifactName/2.2/artifactName-2.2.pom
                artifactName/maven-metadata-local.xml
            """.trimIndent(), files.joinToString("\n")
        )

        val pom = groupDir / "artifactName/2.2/artifactName-2.2.pom"
        assertEquals(expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>amper.test</groupId>
              <artifactId>artifactName</artifactId>
              <version>2.2</version>
              <name>jvm-publish</name>
              <dependencies>
                <dependency>
                  <groupId>io.ktor</groupId>
                  <artifactId>ktor-client-core</artifactId>
                  <version>2.3.9</version>
                  <scope>compile</scope>
                </dependency>
                <dependency>
                  <groupId>io.ktor</groupId>
                  <artifactId>ktor-client-java</artifactId>
                  <version>2.3.9</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-coroutines-core</artifactId>
                  <version>1.6.0</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-core</artifactId>
                  <version>1.6.3</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-json</artifactId>
                  <version>1.6.3</version>
                  <scope>provided</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlinx</groupId>
                  <artifactId>kotlinx-serialization-cbor</artifactId>
                  <version>1.6.3</version>
                  <scope>compile</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-stdlib</artifactId>
                  <version>${UsedVersions.kotlinVersion}</version>
                  <scope>runtime</scope>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent(), pom.readText().trim())
    }

    @Test
    fun `jvm publish multi-module to maven local`() = runTestWithCollector {
        val m2repository = Path(System.getProperty("user.home"), ".m2/repository")
        val groupDir = m2repository.resolve("amper").resolve("test")
        groupDir.deleteRecursively()

        val projectContext = setupTestDataProject("jvm-publish-multimodule")
        val backend = AmperBackend(projectContext)
        backend.runTask(TaskName(":main-lib:publishJvmToMavenLocal"))

        val files = groupDir.walk()
            .onEach {
                check(it.fileSize() > 0) { "File should not be empty: $it" }
            }
            .map { it.relativeTo(groupDir).pathString.replace('\\', '/') }
            .sorted()

        // note that publishing of main-lib module triggers all other modules (by design)
        assertEquals(
            """
                jvm-lib/1.2.3/_remote.repositories
                jvm-lib/1.2.3/jvm-lib-1.2.3-sources.jar
                jvm-lib/1.2.3/jvm-lib-1.2.3.jar
                jvm-lib/1.2.3/jvm-lib-1.2.3.pom
                jvm-lib/maven-metadata-local.xml
                kmp-lib-jvm/1.2.3/_remote.repositories
                kmp-lib-jvm/1.2.3/kmp-lib-jvm-1.2.3-sources.jar
                kmp-lib-jvm/1.2.3/kmp-lib-jvm-1.2.3.jar
                kmp-lib-jvm/1.2.3/kmp-lib-jvm-1.2.3.pom
                kmp-lib-jvm/maven-metadata-local.xml
                main-lib/1.2.3/_remote.repositories
                main-lib/1.2.3/main-lib-1.2.3-sources.jar
                main-lib/1.2.3/main-lib-1.2.3.jar
                main-lib/1.2.3/main-lib-1.2.3.pom
                main-lib/maven-metadata-local.xml
            """.trimIndent(), files.joinToString("\n")
        )

        val pom = groupDir / "main-lib/1.2.3/main-lib-1.2.3.pom"
        assertEquals(expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>amper.test</groupId>
              <artifactId>main-lib</artifactId>
              <version>1.2.3</version>
              <name>main-lib</name>
              <dependencies>
                <dependency>
                  <groupId>amper.test</groupId>
                  <artifactId>jvm-lib</artifactId>
                  <version>1.2.3</version>
                  <scope>compile</scope>
                </dependency>
                <dependency>
                  <groupId>amper.test</groupId>
                  <artifactId>kmp-lib-jvm</artifactId>
                  <version>1.2.3</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-stdlib</artifactId>
                  <version>${UsedVersions.kotlinVersion}</version>
                  <scope>runtime</scope>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent(), pom.readText().trim())
    }

    @Test
    fun `jvm publish to http no authentication`() = runTestWithCollector {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }

        withFileServer(www) { baseUrl ->
            val projectContext = setupTestDataProject("jvm-publish", copyToTemp = true)

            val moduleYaml = projectContext.projectRoot.path.resolve("module.yaml")
            moduleYaml.writeText(moduleYaml.readText().replace("REPO_URL", baseUrl))

            val backend = AmperBackend(projectContext)
            backend.runTask(TaskName(":jvm-publish:publishJvmToRepoNoCredentialsId"))

            val groupDir = www.resolve("amper").resolve("test")
            val files = groupDir.walk()
                .map {
                    check(it.fileSize() > 0) {
                        "File should not be empty: $it"
                    }
                    it
                }
                .map { it.relativeTo(groupDir).pathString.replace('\\', '/') }
                .sorted()
            assertEquals(
                """
                    artifactName/2.2/artifactName-2.2-sources.jar
                    artifactName/2.2/artifactName-2.2-sources.jar.md5
                    artifactName/2.2/artifactName-2.2-sources.jar.sha1
                    artifactName/2.2/artifactName-2.2-sources.jar.sha256
                    artifactName/2.2/artifactName-2.2-sources.jar.sha512
                    artifactName/2.2/artifactName-2.2.jar
                    artifactName/2.2/artifactName-2.2.jar.md5
                    artifactName/2.2/artifactName-2.2.jar.sha1
                    artifactName/2.2/artifactName-2.2.jar.sha256
                    artifactName/2.2/artifactName-2.2.jar.sha512
                    artifactName/2.2/artifactName-2.2.pom
                    artifactName/2.2/artifactName-2.2.pom.md5
                    artifactName/2.2/artifactName-2.2.pom.sha1
                    artifactName/2.2/artifactName-2.2.pom.sha256
                    artifactName/2.2/artifactName-2.2.pom.sha512
                    artifactName/maven-metadata.xml
                    artifactName/maven-metadata.xml.md5
                    artifactName/maven-metadata.xml.sha1
                    artifactName/maven-metadata.xml.sha256
                    artifactName/maven-metadata.xml.sha512
            """.trimIndent(), files.joinToString("\n")
            )
        }
    }

    @Test
    fun `jvm publish to http password authentication`() = runTestWithCollector {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }
        val authenticator = object : BasicAuthenticator("www-realm") {
            override fun checkCredentials(username: String, password: String): Boolean {
                return username == "http-user" && password == "http-password"
            }
        }

        withFileServer(www, authenticator) { baseUrl ->
            val projectContext = setupTestDataProject("jvm-publish", copyToTemp = true)

            val moduleYaml = projectContext.projectRoot.path.resolve("module.yaml")
            moduleYaml.writeText(moduleYaml.readText().replace("REPO_URL", baseUrl))

            val backend = AmperBackend(projectContext)
            backend.runTask(TaskName(":jvm-publish:publishJvmToRepoId"))

            val groupDir = www.resolve("amper").resolve("test")
            val files = groupDir.walk()
                .map {
                    check(it.fileSize() > 0) {
                        "File should not be empty: $it"
                    }
                    it
                }
                .map { it.relativeTo(groupDir).pathString.replace('\\', '/') }
                .sorted()
            assertEquals(
                """
                    artifactName/2.2/artifactName-2.2-sources.jar
                    artifactName/2.2/artifactName-2.2-sources.jar.md5
                    artifactName/2.2/artifactName-2.2-sources.jar.sha1
                    artifactName/2.2/artifactName-2.2-sources.jar.sha256
                    artifactName/2.2/artifactName-2.2-sources.jar.sha512
                    artifactName/2.2/artifactName-2.2.jar
                    artifactName/2.2/artifactName-2.2.jar.md5
                    artifactName/2.2/artifactName-2.2.jar.sha1
                    artifactName/2.2/artifactName-2.2.jar.sha256
                    artifactName/2.2/artifactName-2.2.jar.sha512
                    artifactName/2.2/artifactName-2.2.pom
                    artifactName/2.2/artifactName-2.2.pom.md5
                    artifactName/2.2/artifactName-2.2.pom.sha1
                    artifactName/2.2/artifactName-2.2.pom.sha256
                    artifactName/2.2/artifactName-2.2.pom.sha512
                    artifactName/maven-metadata.xml
                    artifactName/maven-metadata.xml.md5
                    artifactName/maven-metadata.xml.sha1
                    artifactName/maven-metadata.xml.sha256
                    artifactName/maven-metadata.xml.sha512
            """.trimIndent(), files.joinToString("\n")
            )
        }
    }

    @Test
    fun `jvm publish adds to maven-metadata xml`() = runTestWithCollector {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }
        val authenticator = object : BasicAuthenticator("www-realm") {
            override fun checkCredentials(username: String, password: String): Boolean {
                return username == "http-user" && password == "http-password"
            }
        }

        withFileServer(www, authenticator) { baseUrl ->
            // deploy version 2.2
            run {
                val projectContext = setupTestDataProject("jvm-publish", copyToTemp = true)

                val moduleYaml = projectContext.projectRoot.path.resolve("module.yaml")
                moduleYaml.writeText(moduleYaml.readText().replace("REPO_URL", baseUrl))

                AmperBackend(projectContext).runTask(TaskName(":jvm-publish:publishJvmToRepoId"))
            }
            // deploy version 2.3
            run {
                val projectContext = setupTestDataProject("jvm-publish", copyToTemp = true)

                val moduleYaml = projectContext.projectRoot.path.resolve("module.yaml")
                moduleYaml.writeText(moduleYaml.readText().replace("REPO_URL", baseUrl).replace("2.2", "2.3"))

                AmperBackend(projectContext).runTask(TaskName(":jvm-publish:publishJvmToRepoId"))
            }

            val mavenMetadataXml = www.resolve("amper/test/artifactName/maven-metadata.xml")
            assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>amper.test</groupId>
                  <artifactId>artifactName</artifactId>
                  <versioning>
                    <release>2.3</release>
                    <versions>
                      <version>2.2</version>
                      <version>2.3</version>
                    </versions>
                    <lastUpdated>TIMESTAMP</lastUpdated>
                  </versioning>
                </metadata>

            """.trimIndent(), mavenMetadataXml.readText()
                .replace(Regex("<lastUpdated>\\d+</lastUpdated>"), "<lastUpdated>TIMESTAMP</lastUpdated>"))
        }
    }

    @Test
    fun `jvm publish handles snapshot versioning`() = runTestWithCollector {
        val www = tempRoot.resolve("www-root").also { it.createDirectories() }
        val authenticator = object : BasicAuthenticator("www-realm") {
            override fun checkCredentials(username: String, password: String): Boolean {
                return username == "http-user" && password == "http-password"
            }
        }

        withFileServer(www, authenticator) { baseUrl ->
            suspend fun deployVersion(version: String) {
                val projectContext = setupTestDataProject("jvm-publish", copyToTemp = true)

                val moduleYaml = projectContext.projectRoot.path.resolve("module.yaml")
                moduleYaml.writeText(moduleYaml.readText().replace("REPO_URL", baseUrl).replace("2.2", version))

                AmperBackend(projectContext).runTask(TaskName(":jvm-publish:publishJvmToRepoId"))
            }

            deployVersion("1.0")
            deployVersion("2.0-SNAPSHOT")
            deployVersion("2.0-SNAPSHOT")

            assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>amper.test</groupId>
                  <artifactId>artifactName</artifactId>
                  <versioning>
                    <release>1.0</release>
                    <versions>
                      <version>1.0</version>
                      <version>2.0-SNAPSHOT</version>
                    </versions>
                    <lastUpdated>TIMESTAMP</lastUpdated>
                  </versioning>
                </metadata>

            """.trimIndent(), www.resolve("amper/test/artifactName/maven-metadata.xml").readText()
                .replace(Regex("<lastUpdated>\\d+</lastUpdated>"), "<lastUpdated>TIMESTAMP</lastUpdated>"))

            val lastVersion = www.resolve("amper/test/artifactName/2.0-SNAPSHOT").listDirectoryEntries()
                .map { it.name }
                .sorted()
                .last { it.startsWith("artifactName-") && it.endsWith(".jar") }
                .removePrefix("artifactName-")
                .removeSuffix(".jar")

            assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <groupId>amper.test</groupId>
                  <artifactId>artifactName</artifactId>
                  <versioning>
                    <lastUpdated>TIMESTAMP</lastUpdated>
                    <snapshot>
                      <timestamp>TIMESTAMP</timestamp>
                      <buildNumber>2</buildNumber>
                    </snapshot>
                    <snapshotVersions>
                      <snapshotVersion>
                        <extension>jar</extension>
                        <value>$lastVersion</value>
                        <updated>TIMESTAMP</updated>
                      </snapshotVersion>
                      <snapshotVersion>
                        <classifier>sources</classifier>
                        <extension>jar</extension>
                        <value>$lastVersion</value>
                        <updated>TIMESTAMP</updated>
                      </snapshotVersion>
                      <snapshotVersion>
                        <extension>pom</extension>
                        <value>$lastVersion</value>
                        <updated>TIMESTAMP</updated>
                      </snapshotVersion>
                    </snapshotVersions>
                  </versioning>
                  <version>2.0-SNAPSHOT</version>
                </metadata>

            """.trimIndent(), www.resolve("amper/test/artifactName/2.0-SNAPSHOT/maven-metadata.xml").readText()
                .replace(Regex("<lastUpdated>\\d+</lastUpdated>"), "<lastUpdated>TIMESTAMP</lastUpdated>")
                .replace(Regex("<updated>\\d+</updated>"), "<updated>TIMESTAMP</updated>")
                .replace(Regex("<timestamp>[\\d.]+</timestamp>"), "<timestamp>TIMESTAMP</timestamp>")
            )
        }
    }

    @Test
    @WindowsOnly
    fun `simple multiplatform cli on windows`() = runTestWithCollector {
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

    @Test
    fun `custom task dependencies`() = runTestWithCollector {
        val projectContext = setupTestDataProject("custom-task-dependencies")
        AmperBackend(projectContext).showTasks()

        assertStdoutContains("task :main-lib:publishJvmToMavenLocal -> :main-lib:jarJvm, :main-lib:sourcesJarJvm, :utils:testJvm, :main-lib:testJvm")
    }

    private val specialCmdChars = "&()[]{}^=;!'+,`~"
    private val argumentsWithSpecialChars = listOf(
        "simple123",
        "my arg2",
        "my arg3 :\"'<>\$ && || ; \"\" $specialCmdChars ${specialCmdChars.chunked(1).joinToString(" ")}",
    )

    @Test
    fun `simple multiplatform cli sources jars`() = runTestWithCollector {
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

    @Test
    fun `jvm transitive dependencies`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-transitive-dependencies")

        // 1. Check compile classpath
        val result = AmperBackend(projectContext)
            .runTask(TaskName(":app:resolveDependenciesJvm"))
            ?.getOrNull() as? ResolveExternalDependenciesTask.Result

        assertNotNull(result, "unexpected result absence for :app:resolveDependenciesJvm")

        // Comparing the lists since the order of libraries on classpath is important
        assertEquals(
            listOf(
                // 1. At first, follow direct dependencies of exported module dependencies
                // exported dependency of D1_exp (app -> D1_exp)
                "picocli-4.7.6.jar",
                // exported dependency of E1_exp (app -> E1_exp)
                "checker-qual-3.44.0.jar",
                // 2. Then, direct dependencies of not-exported module dependencies
                // exported dependency of B1 (app -> B1)
                "lombok-1.18.32.jar",
                // exported dependency of C1 (app -> C1)
                "tensorflow-lite-api-2.16.1.aar",
                // 3. Then, direct dependencies of exported module dependencies of the second layer (keeping order of the first layer)
                // exported dependency of E1_exp (app -> E1_exp -> E2_exp)
                "jackson-core-2.17.1.jar",
                // exported dependency of C2_exp (app -> C1 -> C2_exp)
                "simple-xml-safe-2.7.1.jar",
                // 3. Then, direct dependencies of exported module dependencies of the third layer
                "hamcrest-2.2.jar",
        ),
            result.compileClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved compile dependencies"
        )

        // 2. Check runtime classpath composed after compilation tasks are finished
        val runtimeClasspathResult = AmperBackend(projectContext)
                .runTask(TaskName(":app:runtimeClasspathJvm"))
                ?.getOrNull() as? JvmRuntimeClasspathTask.Result

        assertNotNull(runtimeClasspathResult, "unexpected result absence for :app:runtimeClasspathJvm")

        val runtimeClassPath = runtimeClasspathResult.jvmRuntimeClasspath

        val expectedRuntimeClasspath = listOf(
            // 1. At first, follow direct dependencies of exported module dependencies:
            // dependencies of D1_exp (app -> D1_exp)
            "picocli-4.7.6.jar", "osgi.annotation-8.1.0.jar",
            // dependencies of E1_exp (app -> E1_exp)
            "checker-qual-3.44.0.jar", "jakarta.annotation-api-3.0.0.jar",
            // 2. Then, direct dependencies of not-exported module dependencies:
            // dependencies of B1 (app -> B1)
            "lombok-1.18.32.jar", "jcharset-2.1.jar",
            // dependencies of C1 (app -> C1)
            "tensorflow-lite-api-2.16.1.aar", "slf4j-api-2.0.13.jar",
            // 3. Then, direct dependencies of the exported module dependencies (the 2-nd layer, keeping order of the first layer):
            // dependencies of D2 (app -> D1_exp -> D2)
            "asm-9.7.jar", /*"annotations-24.1.0.jar",*/
            // dependencies of E2_exp (app -> E1_exp -> E2_exp)
            "jackson-core-2.17.1.jar", "objenesis-test-3.4.jar",
            // dependencies of B2 (app -> B1 -> B2)
            "tinylog-api-2.7.0.jar", "commons-text-1.12.0.jar",
            // dependencies of C2_exp (app -> C1 -> C2_exp)
            "simple-xml-safe-2.7.1.jar", "jakarta.activation-api-2.1.3.jar",
            // 4. Then, direct dependencies of exported module dependencies (the 4-th layer):
            // dependencies of (app -> B1 -> B2 -> "commons-text-1.12.0.jar")
            "commons-lang3-3.14.0.jar",
            // dependencies of C3_exp (app -> C1 -> C2_exp -> C3_exp)
            "hamcrest-2.2.jar", "apiguardian-api-1.1.2.jar",
        )

        assertEquals(
            expectedRuntimeClasspath,
            result.runtimeClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved direct runtime dependencies of JVM app module"
        )

        assertEquals(
            expectedRuntimeClasspath,
            runtimeClassPath
                .withoutImplicitAmperLibs()
                // filtering out module compile result
                .filterNot { it.startsWith(projectContext.buildOutputRoot.path) }
                .map { it.name },
            "Unexpected list of resolved runtime dependencies"
        )

        val runtimeClasspathViaTask = AmperBackend(projectContext)
            .runTask(TaskName(":app:runtimeClasspathJvm"))
            ?.getOrNull() as? JvmRuntimeClasspathTask.Result

        assertNotNull(runtimeClasspathViaTask, "unexpected result absence for :app:runtimeClasspathJvm")

        // Check correct module order in runtime classpath
        val modules = listOf("app", "D1_exp", "E1_exp", "B1", "C1", "D2", "E2_exp", "B2", "C2_exp", "C3_exp").map { "$it-jvm.jar"}

        assertEquals(
            modules + expectedRuntimeClasspath,
            runtimeClasspathViaTask.jvmRuntimeClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved runtime dependencies (via task)"
        )

        val find = "finished ':app:resolveDependenciesJvm' in"
        assertInfoLogStartsWith(find)
    }

    @Test
    fun `jvm runtime classpath conflict resolution`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-runtime-classpath-conflict-resolution")

        val result = AmperBackend(projectContext)
            .runTask(TaskName(":B2:resolveDependenciesJvm"))
            ?.getOrNull() as? ResolveExternalDependenciesTask.Result
            ?: error("unexpected result absence for :B2:resolveDependenciesJvm")

        // should be only one version of commons-io, the highest version
        assertEquals(
            listOf("commons-io-2.16.1.jar"),
            result.runtimeClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved runtime dependencies"
        )
    }

    private fun List<Path>.withoutImplicitAmperLibs() =
        filterNot { it.name.startsWith("kotlin-stdlib-") || it.name.startsWith("annotations-") }

    private suspend fun withFileServer(wwwRoot: Path, authenticator: Authenticator? = null, block: suspend (baseUrl: String) -> Unit) {
        val httpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10)
        try {
            val context = httpServer.createContext("/") { exchange ->
                fun respond(code: Int, content: ByteArray = ByteArray(0)) {
                    exchange.sendResponseHeaders(code, content.size.toLong())
                    exchange.responseBody.use { it.write(content) }
                }

                try {

                    val fsPath = wwwRoot.resolve(exchange.requestURI.path.trim('/')).normalize()
                    require(fsPath.startsWith(wwwRoot)) {
                        "'$fsPath' must start with '$wwwRoot'"
                    }

                    println("WWW: ${exchange.requestMethod} ${exchange.requestURI}")

                    when (exchange.requestMethod) {
                        "GET" -> if (fsPath.isRegularFile()) {
                            respond(200, fsPath.readBytes())
                        } else {
                            respond(404)
                        }

                        "PUT" -> {
                            fsPath.parent.createDirectories()
                            val bytes = exchange.requestBody.use { it.readBytes() }
                            val contentLength = exchange.requestHeaders.getFirst("Content-Length").toInt()
                            check(bytes.size == contentLength) {
                                "PUT $fsPath: body size '${bytes.size}' content-length '$contentLength'"
                            }

                            fsPath.writeBytes(bytes)

                            respond(200)
                        }

                        else -> respond(405)
                    }

                } catch (t: Throwable) {
                    errorCollectorExtension.addException(t)
                    t.printStackTrace()
                    throw t
                }
            }

            if (authenticator != null) {
                context.setAuthenticator(authenticator)
            }

            httpServer.start()

            block("http://127.0.0.1:${httpServer.address.port}")
        } finally {
            httpServer.stop(0)
        }
    }

    @Disabled("Metadata compilation doesn't 100% work at the moment, because we need DR to support multi-platform dependencies")
    @Test
    fun `simple multiplatform cli metadata`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = emptyList())
        val backend = AmperBackend(projectContext)

        val compileMetadataJvmMain = TaskName(":shared:compileMetadataJvm")
        backend.runTask(compileMetadataJvmMain)
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
private fun CliContext.taskOutputPath(taskName: TaskName): Path =
    buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_")
