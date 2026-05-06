/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertJavaIncrementalCompilationState
import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.assertStdoutDoesNotContain
import org.jetbrains.amper.cli.test.utils.getTaskOutputPath
import org.jetbrains.amper.cli.test.utils.readTelemetrySpans
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.spans.assertEachKotlinNativeCompilationSpan
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@ParameterizedTest(name = "{displayName}; jps={0}")
@ValueSource(booleans = [true, false])
@Target(AnnotationTarget.FUNCTION)
private annotation class RunWithAndWithoutJic

class AmperBuildTest : AmperCliTestBase() {

    @RunWithAndWithoutJic
    fun `build command succeeds in jvm-default-compiler-settings`(compileJavaIncrementally: Boolean) = runSlowTest {
        runCliWithOrWithoutJps(
            projectRoot = testProject("jvm-default-compiler-settings"),
            "build",
            compileJavaIncrementally = compileJavaIncrementally,
        )
    }

    @Test
    fun `build command produces a jar for jvm in kmp project`() = runSlowTest {
        val result = runCli(
            projectDir = testProject("multiplatform-input"),
            "build", "-p", "jvm",
        )

        assertTrue {
            val file = result.getTaskOutputPath(":shared:jarJvm") / "shared-jvm.jar"
            file.exists()
        }
    }

    @RunWithAndWithoutJic
    fun `build jar with main class`(useJavaIncrementalCompilation: Boolean) = runSlowTest {
        val result = runCliWithOrWithoutJps(
            projectRoot = testProject("java-kotlin-mixed"),
            "build",
            compileJavaIncrementally = useJavaIncrementalCompilation,
            )

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
            projectDir = testProject("multi-module-failed-kotlinc-compilation"),
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        val file = r.projectDir.resolve("shared/src/World.kt").toUri()
        // Uses old style of reporting (< 2.4.0-Beta2), should be updated if the default Kotlin version changes.
        r.assertStderrContains("ERROR (shared) $file:2:26 Unresolved reference 'XXXX'")

        val lastLine = r.stderr.lines().last { it.isNotBlank() }
        assertEquals(
            "ERROR: Task ':shared:compileJvm' failed: Kotlin compilation failed with 1 errors (see above)".trimIndent(),
            lastLine,
        )
    }

    @Test
    fun `simple multiplatform cli should compile windows on any platform`() = runSlowTest {
        val projectContext = testProject("simple-multiplatform-cli")
        val result = runCli(projectDir = projectContext, "build", "--platform=mingwX64")

        assertTrue("build must generate a 'windows-cli.exe' file somewhere") {
            result.buildDir.walk().any { it.name == "windows-cli.exe" }
        }
    }

    @Test
    fun `failed dependency resolution message`() = runSlowTest {
        val r = runCli(
            projectDir = testProject("multi-module-failed-resolve"),
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        val actualStderr = r.stderr.lines().filter { it.isNotBlank() }.joinToString("\n")
        val sharedModule = r.projectDir.resolve("shared/module.yaml")

        // Prepend \n manually, since trimIndent will remove it.
        val sharedErrorPart = "\n" + """
            $sharedModule:6:5: Unable to resolve dependency org.junit.jupiter:junit-jupiter-api:9999
              Unable to download checksums of file junit-jupiter-api-9999.pom
              Unable to download checksums of file junit-jupiter-api-9999.module
            Repositories used for resolution:
              - https://repo1.maven.org/maven2
              - https://maven.google.com
            """.trimIndent()
        
        // Could be any of them:
        fun errorPrefix(module: String, task: String) = 
            "ERROR: Task ':$module:$task' failed: Unable to resolve dependencies for module $module:"
        val expectedOf = listOf(
            errorPrefix("app", "resolveDependenciesJvm") + sharedErrorPart.repeat(1),
            errorPrefix("app", "resolveDependenciesJvmTest") + sharedErrorPart.repeat(1),
            errorPrefix("shared", "resolveDependenciesJvm") + sharedErrorPart.repeat(2),
            errorPrefix("shared", "resolveDependenciesJvmTest") + sharedErrorPart.repeat(2),
        )

        if (actualStderr !in expectedOf) {
            val expectedActualComparisonText = buildString {
                expectedOf.forEachIndexed { index, it ->
                    appendLine(it.prependIndent("EXPECTED$index> "))
                    appendLine()
                }
                appendLine(actualStderr.prependIndent("ACTUAL> "))
            }

            // produce IDEA-viewable diff
            println(expectedActualComparisonText)

            fail("Amper error doesn't match expected dependency resolution errors:\n$expectedActualComparisonText")
        }
    }

    @Test
    fun `run build issues warning about unsupported build variant`() = runSlowTest {
        val projectRoot = testProject("jvm-resources")

        val result1 = runCli(
            projectDir = projectRoot,
            "build", "-v", "debug",
        )

        result1.assertStdoutContains(
            "Explicit -v/--variant argument is ignored because none of the selected platforms (jvm) support build variants."
        )

        val result2 = runCli(
            projectDir = projectRoot,
            "build",
        )

        result2.assertStdoutDoesNotContain(
            "Explicit -v/--variant argument is ignored because none of the selected platforms (jvm) support build variants."
        )
    }

    @Test
    fun `native linker options are respected`() = runSlowTest {
        val projectRoot = testProject("native-linker-options")
        val result = runCli(projectDir = projectRoot, "build")

        result.readTelemetrySpans().assertEachKotlinNativeCompilationSpan {
            hasCompilerArgument("-linker-option=-Wl,--as-needed")
            hasCompilerArgument("-linker-option=-Wl,-Bstatic")
            hasCompilerArgument("-linker-option=-lz")
        }
    }

    @Test
    fun `kotlin compiler dev version`() = runSlowTest {
        val projectContext = testProject("kotlin-dev-version")
        runCli(projectDir = projectContext, "build") // just test that it builds
    }

    @Test
    fun `kotlin errors are reported structurally`() = runSlowTest {
        val projectContext = testProject("kotlin-diagnostics-errors")
        val result = runCli(
            projectDir = projectContext,
            "build",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        val filePath = Path("src/main.kt").pathString

        result.assertStderrContains("""
              ╭─ ERROR: Cannot infer type for type parameter 'B'. Specify it explicitly.
              │ → $filePath:8:9 (kotlin-diagnostics-errors)
              │
            8 │     "a" to unknownValue
              │         ⌃⌃
              ╰─
        """.trimIndent())

        result.assertStderrContains("""
              ╭─ ERROR: Unresolved reference 'unknownValue'.
              │ → $filePath:8:12 (kotlin-diagnostics-errors)
              │
            8 │     "a" to unknownValue
              │            ⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃
              ╰─
        """.trimIndent())

        result.assertStderrContains("""
               ╭─ ERROR: Argument type mismatch: actual type is 'String', but 'Int' was expected.
               │ → $filePath:10:9 (kotlin-diagnostics-errors)
               │
               │         ⌄⌄⌄
            10 │     foo(""${'"'}
            11 │         multiline
            12 │     ""${'"'}.trimIndent())
               │ ⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃⌃
               ╰─
        """.trimIndent())
    }

    @Test
    fun `kotlin warnings are reported structurally`() = runSlowTest {
        val projectContext = testProject("kotlin-diagnostics-warnings")
        val result = runCli(projectDir = projectContext, "build")
        val filePath = Path("src/main.kt").pathString
        result.assertStdoutContains("""
              ╭─ WARNING: Unused return value of 'foo'.
              │ → $filePath:9:5 (kotlin-diagnostics-warnings)
              │
            9 │     foo()
              │     ⌃⌃⌃
              ╰─
        """.trimIndent())

        result.assertStdoutContains("""
               ╭─ WARNING: Expression is unused.
               │ → $filePath:11:5 (kotlin-diagnostics-warnings)
               │
               │     ⌄⌄⌄
            11 │     ""${'"'}
            12 │         multiline
            13 │     ""${'"'}
               │ ⌃⌃⌃⌃⌃⌃⌃
               ╰─
        """.trimIndent())
    }

    private suspend fun runCliWithOrWithoutJps(
        projectRoot: Path,
        vararg args: String,
        compileJavaIncrementally: Boolean,
    ): AmperCliResult {
        val result = runCli(
            projectDir = projectRoot,
            *args,
            amperJvmArgs = listOf("-Dorg.jetbrains.amper.jic=${compileJavaIncrementally}"),
        )
        result.assertJavaIncrementalCompilationState(compileJavaIncrementally)
        return result
    }
}
