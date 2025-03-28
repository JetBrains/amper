/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.getTaskOutputPath
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperBuildTest : AmperCliTestBase() {

    @Test
    fun `build command produces a jar for jvm`() = runSlowTest {
        val result = runCli(
            projectRoot = testProject("multiplatform-input"),
            "build", "-p", "jvm",
        )

        assertTrue {
            val file = result.getTaskOutputPath(":shared:jarJvm") / "shared-jvm.jar"
            file.exists()
        }
    }

    @Test
    fun `build jar with main class`() = runSlowTest {
        val result = runCli(projectRoot = testProject("java-kotlin-mixed"), "build")

        val jarPath = result.getTaskOutputPath(":java-kotlin-mixed:jarJvm").resolve("java-kotlin-mixed-jvm.jar")
        assertTrue(jarPath.isRegularFile(), "${jarPath.pathString} should exist and be a file")

        JarFile(jarPath.toFile()).use { jar ->
            val mainClass = jar.manifest.mainAttributes[Attributes.Name.MAIN_CLASS] as? String
            assertNotNull(mainClass, "The ${Attributes.Name.MAIN_CLASS} attribute should be present")
            assertEquals("bpkg.MainKt", mainClass)

            val entryNames = jar.entries().asSequence().map { it.name }.toList()
            val expectedEntriesInOrder = listOf(
                "META-INF/MANIFEST.MF",
                "META-INF/",
                "META-INF/main.kotlin_module",
                "apkg/",
                "apkg/AClass.class",
                "bpkg/",
                "bpkg/BClass.class",
                "bpkg/MainKt.class",
            )
            assertEquals(expectedEntriesInOrder, entryNames)
        }
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
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val expected2 = """
            ERROR: Task ':app:resolveDependenciesJvmTest' failed: Unable to resolve dependencies for module app:
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val expected3 = """
            ERROR: Task ':shared:resolveDependenciesJvm' failed: Unable to resolve dependencies for module shared:
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
        """.trimIndent()
        val expected4 = """
            ERROR: Task ':shared:resolveDependenciesJvmTest' failed: Unable to resolve dependencies for module shared:
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
            Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.pom for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
              Unable to download checksums of file junit-jupiter-api-9999.module for dependency org.junit.jupiter:junit-jupiter-api:9999 (repositories: https://repo1.maven.org/maven2, https://maven.google.com, https://maven.pkg.jetbrains.space/public/p/compose/dev)
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
}
