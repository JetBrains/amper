/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.test.MacOnly
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperCliTest: AmperCliTestBase() {

    @Test
    fun smoke() = runSlowTest {
        runCli(testProject("jvm-kotlin-test-smoke"), "tasks")
    }

    @Test
    fun `run command help prints dash dash`() = runSlowTest {
        val r = runCli(testProject("jvm-kotlin-test-smoke"), "run", "--help")

        // Check that '--' is printed before program arguments
        val string = "Usage: amper run [<options>] -- [<app_arguments>]..."

        assertTrue("There should be '$string' in `run --help` output") {
            r.stdout.lines().any { it == string }
        }
    }

    @Test
    fun `graceful failure on unknown task name`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-kotlin-test-smoke"),
            "task", "unknown",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val errorMessage = "ERROR: Task 'unknown' was not found in the project"

        assertTrue("Expected stderr to contain the message: '$errorMessage'") {
            errorMessage in r.stderr
        }
    }

    @Test
    fun `graceful failure on unknown task name with suggestions`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-kotlin-test-smoke"),
            "task", "compile",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val errorMessage = """
            ERROR: Task 'compile' was not found in the project, maybe you meant one of:
               :jvm-kotlin-test-smoke:compileJvm
               :jvm-kotlin-test-smoke:compileJvmTest
               :jvm-kotlin-test-smoke:compileMetadataCommon
               :jvm-kotlin-test-smoke:compileMetadataCommonTest
               :jvm-kotlin-test-smoke:compileMetadataJvm
               :jvm-kotlin-test-smoke:compileMetadataJvmTest
        """.trimIndent()

        assertTrue("Expected stderr to contain the message:\n$errorMessage\n\nActual stderr:\n${r.stderr}") {
            errorMessage in r.stderr
        }
    }

    @Test
    fun modules() = runSlowTest {
        val r = runCli(testProject("simple-multiplatform-cli"), "modules")

        assertModulesList(r, listOf(
            "jvm-cli",
            "linux-cli",
            "macos-cli",
            "shared",
            "utils",
            "windows-cli",
        ))
    }

    @Test
    fun `failed kotlinc compilation message`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multi-module-failed-kotlinc-compilation"),
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val lastLines = r.stderr.lines().filter { it.isNotBlank() }.takeLast(2)

        val file = r.projectRoot.resolve("shared/src/World.kt").toUri()

        assertEquals("""
            ERROR: Task ':shared:compileJvm' failed: Kotlin compilation failed:
            e: $file:2:26 Unresolved reference 'XXXX'.
        """.trimIndent(), lastLines.joinToString("\n"))
    }

    @Test
    fun `failed dependency resolution message`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multi-module-failed-resolve"),
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val actualStderr = r.stderr.lines().filter { it.isNotBlank() }.joinToString("\n")

        // could be any of them first
        val expected1 = """
            ERROR: Task ':app:resolveDependenciesJvm' failed: Unable to resolve dependencies for module app:
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val expected2 = """
            ERROR: Task ':app:resolveDependenciesJvmTest' failed: Unable to resolve dependencies for module app:
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val expected3 = """
            ERROR: Task ':shared:resolveDependenciesJvm' failed: Unable to resolve dependencies for module shared:
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val expected4 = """
            ERROR: Task ':shared:resolveDependenciesJvmTest' failed: Unable to resolve dependencies for module shared:
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()

        if (expected1 != actualStderr && expected2 != actualStderr && expected3 != actualStderr && expected4 != actualStderr) {

            val expectedActualComparisonText = buildString {
                appendLine(expected1.prependIndent("EXPECTED1> "))
                appendLine()
                appendLine(expected2.prependIndent("EXPECTED2> "))
                appendLine()
                appendLine(expected3.prependIndent("EXPECTED3> "))
                appendLine()
                appendLine(expected4.prependIndent("EXPECTED4> "))
                appendLine()
                appendLine(actualStderr.prependIndent("ACTUAL> "))
            }

            // produce IDEA-viewable diff
            println(expectedActualComparisonText)

            fail("Amper error doesn't match expected dependency resolution errors:\n$expectedActualComparisonText")
        }
    }

    @Test
    fun `init works`() = runSlowTest {
        val p = tempRoot.resolve("new").also { it.createDirectories() }
        runCli(p, "init", "multiplatform-cli")

        val files = p.walk()
            .map { it.relativeTo(p).pathString.replace("\\", "/") }
            .filter { !it.startsWith("build/") }
            .sorted()
            .joinToString("\n")
        assertEquals(
            """
                .editorconfig
                amper
                amper.bat
                jvm-cli/module.yaml
                linux-cli/module.yaml
                macos-cli/module.yaml
                project.yaml
                shared/module.yaml
                shared/src/World.kt
                shared/src/main.kt
                shared/src@jvm/World.kt
                shared/src@linux/World.kt
                shared/src@macos/World.kt
                shared/src@mingw/World.kt
                shared/test/test.kt
                windows-cli/module.yaml
            """.trimIndent(),
            files
        )
    }

    @Test
    fun `tool jdk jstack runs`() = runSlowTest {
        val p = tempRoot.resolve("new").also { it.createDirectories() }
        val result = runCli(p, "tool", "jdk", "jstack", ProcessHandle.current().pid().toString())

        val requiredSubstring = "Full thread dump"
        assertTrue("stdout should contain '$requiredSubstring':\n${result.stdout}") {
            result.stdout.contains(requiredSubstring)
        }
    }

    @Test
    fun `init won't replace existing files`() = runSlowTest {
        val p = tempRoot.resolve("new").also { it.createDirectories() }
        val exampleFile = p.resolve("jvm-cli/module.yaml").also { it.createParentDirectories() }
        exampleFile.writeText("some text")

        val r = runCli(p, "init", "multiplatform-cli", expectedExitCode = 1, assertEmptyStdErr = false)
        val stderr = r.stderr.replace("\r", "")

        val expectedText = "ERROR: Files already exist in the project root:\n  jvm-cli/module.yaml"
        assertTrue(message = "stderr output does not contain '$expectedText':\n$stderr") {
            stderr.contains(expectedText)
        }

        val files = p.walk()
            .map { it.relativeTo(p).pathString.replace("\\", "/") }
            .sorted()
            .joinToString("\n")
        assertEquals(
            """
                jvm-cli/module.yaml
            """.trimIndent(),
            files
        )
        assertEquals("some text", exampleFile.readText())
    }

    @Test
    fun publish() = runSlowTest {
        val mavenLocalForTest = tempRoot.resolve(".m2.test").also { it.createDirectories() }
        val groupDir = mavenLocalForTest.resolve("amper/test/jvm-publish")

        runCli(
            projectRoot = testProject("jvm-publish"),
            "publish", "mavenLocal",
            amperJvmArgs = listOf("-Dmaven.repo.local=\"${mavenLocalForTest.absolutePathString()}\""),
        )

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
    }

    @Test
    fun `single-module project under an unrelated project`() = runSlowTest {
        val resultNested = runCli(testProject("nested-project-root") / "nested-project", "modules")
        assertModulesList(resultNested, listOf("nested-project"))

        val resultRoot = runCli(testProject("nested-project-root"), "modules")
        assertModulesList(resultRoot, listOf("included-module"))
    }

    @Test
    fun `project including a deep module`() = runSlowTest {
        val result = runCli(testProject("project-root-deep-inclusion"), "modules")
        assertModulesList(result, listOf("deep-module"))
    }

    @Test
    fun `project with denormalized globs`() = runSlowTest {
        val result = runCli(testProject("project-root-denormalized-globs"), "modules")
        assertModulesList(result, listOf("deep", "deep2", "sub1", "sub2", "sub3", "sub4"))
    }

    @Test
    fun `project with both top-level and nested modules`() = runSlowTest {
        val result = runCli(testProject("top-level-and-nested-modules"), "modules")
        assertModulesList(result, listOf("deep-module", "top-level-and-nested-modules"))
    }

    @Test
    fun `project file with path errors`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("project-file-with-errors"),
            "tasks",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        assertContains(r.stdout, "project.yaml:7:5: Glob pattern \"glob-with-no-matches-at-all/*\" doesn't match any Amper module directory under the project root")
        assertContains(r.stdout, "project.yaml:8:5: Glob pattern \"not-a-modul?\" doesn't match any Amper module directory under the project root")
        assertContains(r.stdout, "project.yaml:14:5: The root module is included by default")

        assertContains(r.stderr, "project.yaml:3:5: Unresolved path \"./does-not-exist\"")
        assertContains(r.stderr, "project.yaml:4:5: Unresolved path \"./does/not/exist\"")
        assertContains(r.stderr, "project.yaml:5:5: \"not-a-dir\" is not a directory")
        assertContains(r.stderr, "project.yaml:6:5: Directory \"not-a-module\" doesn't contain an Amper module file")
        assertContains(r.stderr, "project.yaml:9:5: Invalid glob pattern \"broken[syntax\": Missing '] near index 12\n" +
                "broken[syntax\n" +
                "            ^")
        assertContains(r.stderr, "project.yaml:10:5: Invalid glob pattern \"broken[z-a]syntax\": Invalid range near index 7\n" +
                "broken[z-a]syntax\n" +
                "       ^")
        assertContains(r.stderr, "project.yaml:11:5: Invalid glob pattern \"broken[syntax/with/**\": Explicit 'name separator' in class near index 13\n" +
                "broken[syntax/with/**\n" +
                "             ^")
        assertContains(r.stderr, "project.yaml:12:5: Unsupported \"**\" in module glob pattern \"forbidden/**/recursive\". Use multiple single-level \"*\" segments instead to specify the depth exactly.")
        assertContains(r.stderr, "project.yaml:13:5: Directory \"../jvm-default-compiler-settings\" is not under the project root")
        assertContains(r.stderr, "ERROR: aborting because there were errors in the Amper project file, please see above")
    }

    @Test
    fun `invalid project root`() = runSlowTest {
        val explicitRoot = testProject("invalid-project-root")
        val r = runCli(
            projectRoot = explicitRoot,
            "--root",
            explicitRoot.pathString,
            "tasks",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        val expected = "ERROR: The given path '$explicitRoot' is not a valid Amper project root " +
                "directory. Make sure you have a project file or a module file at the root of your Amper project."
        assertEquals(expected, r.stderr.trim())
    }

    @Test
    fun `run works with input for jvm`() = runTest(timeout = 10.minutes) {
        val r = runCli(
            projectRoot = testProject("multiplatform-input"),
            "run", "--module", "jvm-app",
            stdin = ProcessInput.Text("Hello World!\nBye World."),
        )

        assertContains(r.stdout, "Input: 'Hello World!'")
    }

    @Test
    fun `build command produces a jar for jvm`() = runTest(timeout = 10.minutes) {
        runCli(
            projectRoot = testProject("multiplatform-input"),
            "build", "-p", "jvm",
        )

        assertTrue {
            val file = tempRoot / "build" / "tasks" / "_shared_jarJvm" / "shared-jvm.jar"
            file.exists()
        }
    }

    @Test
    @MacOnly
    fun `run works with input for native`() = runTest(timeout = 10.minutes) {
        val r = runCli(
            projectRoot = testProject("multiplatform-input"),
            "run", "--module", "macos-app",
            stdin = ProcessInput.Text("Hello World!\nBye World."),
        )

        assertContains(r.stdout, "Input: 'Hello World!'")
    }

    @Test
    fun `compose resources demo build (android)`() = runSlowTest {
        runCli(
            projectRoot = testProject("compose-resources-demo"),
            "task", ":app-android:buildAndroidDebug",
        )
    }

    @Test
    fun `compose resources demo build and run (jvm)`() = runSlowTest {
        runCli(
            projectRoot = testProject("compose-resources-demo"),
            "test", "--platform=jvm",
        )
    }

    @Test
    @MacOnly
    fun `compose resources demo build (ios)`() = runSlowTest {
        runCli(
            projectRoot = testProject("compose-resources-demo"),
            "build", "--platform=iosSimulatorArm64",
            assertEmptyStdErr = false,  // xcodebuild prints a bunch of warnings (unrelated to resources) for now :(
            copyToTempDir = true,
        )
    }

    @Test
    fun `parcelize android lib - build`() = runSlowTest {
        runCli(testProject("parcelize-android-lib"), "build")
    }

    @Test
    fun `parcelize android lib - test`() = runSlowTest {
        runCli(testProject("parcelize-android-lib"), "test")
    }

    @Test
    fun `parcelize android app - build`() = runSlowTest {
        runCli(testProject("parcelize-android-app"), "build")
    }

    @Test
    fun `parcelize with shared kmp model`() = runSlowTest {
        runCli(testProject("parcelize-shared-kmp-model"), "build")
    }

    @Test
    fun `jvm test with JVM arg`() = runSlowTest {
        val testProject = testProject("jvm-kotlin-test-systemprop")
        runCli(testProject, "test", "--jvm-args=-Dmy.system.prop=hello")

        // should fail without the system prop
        runCli(
            projectRoot = testProject,
            "test",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        // should fail with an incorrect value for the system prop
        runCli(
            projectRoot = testProject,
            "test",
            "--jvm-args=-Dmy.system.prop=WRONG",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
    }

    @Test
    fun `jvm run with JVM arg`() = runSlowTest {
        val testProject = testProject("jvm-run-print-systemprop")
        val result1 = runCli(testProject, "run", "--jvm-args=-Dmy.system.prop=hello")
        assertEquals("my.system.prop=hello", result1.stdout.trim().lines().last())

        val result2 = runCli(testProject, "run", "--jvm-args=\"-Dmy.system.prop=hello world\"")
        assertEquals("my.system.prop=hello world", result2.stdout.trim().lines().last())

        val result3 = runCli(testProject, "run", "--jvm-args=-Dmy.system.prop=hello\\ world")
        assertEquals("my.system.prop=hello world", result3.stdout.trim().lines().last())
    }

    private fun assertModulesList(modulesCommandResult: AmperCliResult, expectedModules: List<String>) {
        // TODO should we have a machine-readable output format without banner/logs location messages?
        // Sometimes there are output lines about waiting for other processes or downloading the distribution or JRE.
        // There are also the output banner and the "logs are there" lines.
        // There may be empty lines in this first part, and there is always an empty line after the logs location line.
        val modules = modulesCommandResult.stdout.lines()
            .dropLastWhile { it.isEmpty() }
            .takeLastWhile { it.isNotEmpty() }
        return assertEquals(expectedModules, modules)
    }
}
