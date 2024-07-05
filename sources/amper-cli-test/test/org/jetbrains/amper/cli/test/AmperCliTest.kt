/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.m2repository
import org.jetbrains.amper.test.TestUtil.runTestInfinitely
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
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

class AmperCliTest: AmperCliTestBase() {
    @Test
    fun smoke() = runTestInfinitely {
        runCli("jvm-kotlin-test-smoke", "tasks")
    }

    @Test
    fun `run command help prints dot dot`() = runTestInfinitely {
        val r = runCli("jvm-kotlin-test-smoke", "run", "--help")

        // Check that '--' is printed before program arguments
        val string = "Usage: amper run [<options>] -- [<program arguments>]..."

        assertTrue("There should be '$string' in `run --help` output") {
            r.stdout.lines().any { it == string }
        }
    }

    @Test
    fun `graceful failure on unknown task name`() = runTestInfinitely {
        val r = runCli("jvm-kotlin-test-smoke", "task", "unknown", expectedExitCode = 1, assertEmptyStdErr = false)

        val errorMessage = "ERROR: Task 'unknown' was not found in the project"

        assertTrue("Expected stderr to contain the message: '$errorMessage'") {
            errorMessage in r.stderr
        }
    }

    @Test
    fun `graceful failure on unknown task name with suggestions`() = runTestInfinitely {
        val r = runCli("jvm-kotlin-test-smoke", "task", "compile", expectedExitCode = 1, assertEmptyStdErr = false)

        val errorMessage = """
            ERROR: Task 'compile' was not found in the project, maybe you meant one of:
               :jvm-kotlin-test-smoke:compileJvm
               :jvm-kotlin-test-smoke:compileJvmTest
               :jvm-kotlin-test-smoke:compileMetadataJvm
               :jvm-kotlin-test-smoke:compileMetadataJvmTest
        """.trimIndent()

        assertTrue("Expected stderr to contain the message:\n$errorMessage\n\nActual stderr:\n${r.stderr}") {
            errorMessage in r.stderr
        }
    }

    @Test
    fun modules() = runTestInfinitely {
        val r = runCli("simple-multiplatform-cli", "modules")

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
    fun `failed kotlinc compilation message`() = runTestInfinitely {
        val projectName = "multi-module-failed-kotlinc-compilation"
        val r = runCli(
            projectName,
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val lastLines = r.stderr.lines().filter { it.isNotBlank() }.takeLast(2)

        val file = testDataRoot.resolve(projectName).resolve("shared/src/World.kt").toUri()

        assertEquals("""
            ERROR: Task ':shared:compileJvm' failed: Kotlin compilation failed:
            e: $file:2:26 Unresolved reference: XXXX
        """.trimIndent(), lastLines.joinToString("\n"))
    }

    @Test
    fun `failed resolve message`() = runTestInfinitely {
        val projectName = "multi-module-failed-resolve"
        val r = runCli(
            projectName,
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val lastLines = r.stderr.lines().filter { it.isNotBlank() }

        // could be any of them first
        val expected1 = """
            ERROR: Task ':shared:resolveDependenciesJvm' failed: Unable to resolve dependencies for module shared:
            Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
            Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val expected2 = """
            ERROR: Task ':shared:resolveDependenciesJvmTest' failed: Unable to resolve dependencies for module shared:
            Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
            Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val actual = lastLines.joinToString("\n")

        if (expected1 != actual && expected2 != actual) {
            println("Full stderr:\n${r.stderr.trim().prependIndent("STDERR ")}\n")

            // produce IDEA-viewable diff
            println(expected1.trim().prependIndent("EXPECTED1> "))
            println()

            println(expected2.trim().prependIndent("EXPECTED2> "))
            println()

            println(actual.trim().prependIndent("ACTUAL> "))
            println()

            fail("assertion failed")
        }
    }

    @Test
    fun `init works`() = runTestInfinitely {
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
    fun `init won't replace existing files`() = runTestInfinitely {
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
    fun publish() = runTestInfinitely {
        val groupDir = m2repository.resolve("amper").resolve("test")
        groupDir.deleteRecursively()

        runCli("jvm-publish", "publish", "mavenLocal")

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
    fun `single-module project under an unrelated project`() = runTestInfinitely {
        val resultNested = runCli(testDataRoot / "nested-project-root" / "nested-project", "modules")
        assertModulesList(resultNested, listOf("nested-project"))

        val resultRoot = runCli(testDataRoot / "nested-project-root", "modules")
        assertModulesList(resultRoot, listOf("included-module"))
    }

    @Test
    fun `project including a deep module`() = runTestInfinitely {
        val result = runCli(testDataRoot / "project-root-deep-inclusion", "modules")
        assertModulesList(result, listOf("deep-module"))
    }

    @Test
    fun `project with both top-level and nested modules`() = runTestInfinitely {
        val result = runCli(testDataRoot / "top-level-and-nested-modules", "modules")
        assertModulesList(result, listOf("deep-module", "top-level-and-nested-modules"))
    }

    @Test
    fun `project file with path errors`() = runTestInfinitely {
        val r = runCli(
            backendTestProjectName = "project-file-with-errors",
            "tasks",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        assertContains(r.stdout, "project.yaml:7:5: Glob pattern \"glob-with-no-matches-at-all/*\" doesn't match any Amper module directory")
        assertContains(r.stdout, "project.yaml:8:5: Glob pattern \"not-a-modul?\" doesn't match any Amper module directory")

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
        assertContains(r.stderr, "ERROR: aborting because there were errors in the Amper project file, please see above")
    }

    @Test
    fun `invalid project root`() = runTestInfinitely {
        val explicitRoot = testDataRoot.resolve("invalid-project-root")
        val r = runCli(
            backendTestProjectName = "invalid-project-root",
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

    private fun assertModulesList(modulesCommandResult: ProcessResult, expectedModules: List<String>) {
        val modules = modulesCommandResult.stdout.lines().dropWhile { it.isNotBlank() }.filter { it.isNotBlank() }
        return assertEquals(expectedModules, modules)
    }

    override val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")
}
