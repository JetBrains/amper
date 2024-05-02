/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.assertEqualsIgnoreLineSeparator
import org.jetbrains.amper.util.OS
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shell scripts test Amper version which is already published and used in `amper-backend-test/testData/projects/shell-scripts`.
 * Other tests use Amper from sources.
 * This is only test where wrappers may download the real Amper version from real location
 */
@OptIn(ExperimentalPathApi::class)
class ShellScriptsTest {
    @TempDir
    lateinit var tempDir: Path

    private val shellScriptExampleProject = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects/shell-scripts")

    @Test
    fun `shell script does not download or extract on subsequent run`() {
        val projectPath = shellScriptExampleProject
        assertTrue { projectPath.isDirectory() }

        val tempProjectRoot = tempDir.resolve("p p").resolve(projectPath.name)
        tempProjectRoot.createDirectories()
        projectPath.copyToRecursively(tempProjectRoot, followLinks = false, overwrite = false)

        val bootstrapCacheDir = tempDir.resolve("boot strap")

        runBuild(workingDir = tempProjectRoot, bootstrapCacheDir = bootstrapCacheDir, "task", ":shell-scripts:runJvm") { output ->
            assertTrue("Process output must contain 'Hello for Shell Scripts Test'. Output:\n$output") {
                output.contains("Hello for Shell Scripts Test")
            }

            assertTrue("Process output must have 'Downloading ' line twice. Output:\n$output") {
                output.lines().count { it.startsWith("Downloading ") } == 2
            }

            assertTrue("Process output must have 'Extracting to $bootstrapCacheDir' line twice. Output:\n$output") {
                output.lines().count { it.startsWith("Extracting to $bootstrapCacheDir") } == 2
            }
        }

        runBuild(workingDir = tempProjectRoot, bootstrapCacheDir = bootstrapCacheDir, "task", ":shell-scripts:runJvm") { output ->
            assertTrue("Process output must contain 'Hello for Shell Scripts Test'. Output:\n$output") {
                output.contains("Hello for Shell Scripts Test")
            }

            assertTrue("Process output must not have 'Downloading ' lines. Output:\n$output") {
                output.lines().none { it.startsWith("Downloading ") }
            }

            assertTrue("Process output must not have 'Extracting ' lines. Output:\n$output") {
                output.lines().none { it.startsWith("Extracting ") }
            }
        }
    }

    @Test
    @Ignore("Not yet supported by published Amper")
    fun `init command writes the same wrappers as published`() {
        val projectPath = shellScriptExampleProject
        assertTrue { projectPath.isDirectory() }

        val tempProjectRoot = tempDir.resolve("p p").resolve(projectPath.name)
        tempProjectRoot.createDirectories()

        val bootstrapCacheDir = tempDir.resolve("boot strap")

        runBuild(workingDir = tempProjectRoot, bootstrapCacheDir = bootstrapCacheDir, "init", "multiplatform-cli") {
        }

        for (wrapperName in listOf("amper", "amper.bat")) {
            val originalFile = shellScriptExampleProject.resolve(wrapperName)
            assertEqualsIgnoreLineSeparator(
                expectedContent = originalFile.readText(),
                actualContent = tempProjectRoot.resolve(wrapperName).readText(),
                originalFile = originalFile,
            )
        }

        if (OS.isUnix) {
            val wrapper = tempProjectRoot.resolve("amper")
            assertTrue("Wrapper must be executable: $wrapper") { wrapper.isExecutable() }
        }
    }

    @Test
    fun `custom java home`() {
        val fakeUserCacheRoot = AmperUserCacheRoot(TestUtil.sharedTestCaches)
        val jdkHome = runBlocking { JdkDownloader.getJdk(fakeUserCacheRoot).homeDir }

        val expectedAmperVersion = cliScript
            .readLines()
            .first { it.startsWith("set amper_version=") || it.startsWith("amper_version=") }
            .substringAfterLast('=')

        runAmperVersion(customJavaHome = jdkHome) { output ->
            val expectedVersionStringOld = "amper version $expectedAmperVersion"
            val expectedVersionString = Regex(
                Regex.escape("JetBrains Amper version $expectedAmperVersion+") +
                        "[A-Fa-f0-9]+")

            assertTrue("Process output must contain '${expectedVersionString.pattern}' or '$expectedVersionStringOld'. Output:\n$output") {
                output.lines().any { it == expectedVersionStringOld || expectedVersionString.matches(it) }
            }

            assertTrue("Process output must have 'Downloading ' line only once (for Amper itself). Output:\n$output") {
                output.lines().count { it.startsWith("Downloading ") } == 1
            }

            // TODO Somehow assert that exactly this JRE is used by amper bootstrap
        }
    }

    @Test
    fun `fails on wrong amper distribution checksum`() {
        assertWrongChecksum(Regex("^(\\s*(set )?amper_sha256=)[0-9a-fA-F]+"))
    }

    @Test
    fun `fails on wrong jre distribution checksum`() {
        assertWrongChecksum(Regex("^(\\s*(set )?jvm_sha256=)[0-9a-fA-F]+"))
    }

    private fun assertWrongChecksum(checksumRegex: Regex) {
        val customScript = tempDir.resolve("script$cliScriptExtension")

        cliScript.readLines()
            .map { line -> checksumRegex.replace(line, "\$1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") }
            .run { customScript.writeLines(this) }
        customScript.toFile().setExecutable(true)
        assertTrue(customScript.isExecutable())

        runAmperVersion(customScript = customScript, expectedExitCode = 1) { output ->
            val expectedContains = "expected checksum aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa but got"
            assertTrue("Process output must contain '$expectedContains' line. Output:\n$output") {
                // cmd break lines at whatever position
                output
                    .replace("\r", "")
                    .replace("\n", "")
                    .contains(expectedContains)
            }
        }
    }

    private fun runBuild(workingDir: Path, bootstrapCacheDir: Path?, vararg args: String, outputAssertions: (String) -> Unit) {
        val process = ProcessBuilder()
            .directory(workingDir.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .also {
                if (bootstrapCacheDir != null) {
                    it.environment()["AMPER_BOOTSTRAP_CACHE_DIR"] = bootstrapCacheDir.pathString
                }
            }
            .command(cliScript.pathString, *args)
            .start()

        process.outputStream.close()
        val processOutput = process.inputStream.readAllBytes().decodeToString()
        val rc = process.waitFor()

        assertEquals(0, rc, "Exit code must be 0. Process output:\n$processOutput")

        outputAssertions(processOutput)
    }

    private fun runAmperVersion(
        customJavaHome: Path? = null,
        customScript: Path? = null,
        expectedExitCode: Int = 0,
        outputAssertions: (String) -> Unit,
    ) {
        val bootstrapDir = tempDir.resolve("boot strap")

        val process = ProcessBuilder()
            .directory(tempDir.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .also {
                it.environment()["AMPER_BOOTSTRAP_CACHE_DIR"] = bootstrapDir.pathString
                if (customJavaHome != null) {
                    it.environment()["AMPER_JAVA_HOME"] = customJavaHome.pathString
                }
            }
            .command((customScript ?: cliScript).pathString, "--version")
            .start()

        process.outputStream.close()
        val processOutput = process.inputStream.readAllBytes().decodeToString()
        val rc = process.waitFor()

        assertEquals(expectedExitCode, rc, "Exit code must be $expectedExitCode, but got $rc. Process output:\n$processOutput")

        outputAssertions(processOutput)
    }

    private val cliScriptExtension = if (OS.isWindows) ".bat" else ""

    private val cliScript: Path by lazy {
        val script = shellScriptExampleProject.resolve("amper$cliScriptExtension")
        assertTrue { script.isExecutable() }
        script
    }
}
