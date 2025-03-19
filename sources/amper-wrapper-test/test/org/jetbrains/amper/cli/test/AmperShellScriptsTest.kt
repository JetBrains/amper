/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.AmperJavaHomeMode
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.generateUnifiedDiff
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.opentest4j.FileInfo
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmperShellScriptsTest : AmperCliWithWrapperTestBase() {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()
    private val tempDir: Path
        get() = tempDirExtension.path

    private val shellScriptExampleProject = Dirs.amperTestProjectsRoot / "shell-scripts"

    @BeforeEach
    fun prepareScript() {
        LocalAmperPublication.setupWrappersIn(tempDir)
    }

    /**
     * It's expected on the start that wrappers and cli dist are published to maven local
     */
    @Test
    fun `shell script does not download or extract on subsequent run`() = runBlocking {
        val templatePath = shellScriptExampleProject
        assertTrue { templatePath.isDirectory() }

        templatePath.copyToRecursively(tempDir, followLinks = false, overwrite = false)

        val bootstrapCacheDir = tempDir.resolve("boot strap")

        val result1 = runAmper(
            workingDir = tempDir,
            args = listOf("run"),
            bootstrapCacheDir = bootstrapCacheDir,
            // We want to test the proper download of the JRE to the bootstrap dir, so we have to unset this
            amperJavaHomeMode = AmperJavaHomeMode.ForceUnset,
        )
        assertTrue("Process output must contain 'Hello for Shell Scripts Test' the first time. Output:\n${result1.stdout}") {
            result1.stdout.contains("Hello for Shell Scripts Test")
        }
        val nDownloadingLines1 = result1.stdout.lines().count { it.startsWith("Downloading ") }
        assertEquals(
            2,
            nDownloadingLines1,
            "Process output must have 'Downloading ' line twice the first time, got $nDownloadingLines1. Output:\n${result1.stdout}"
        )

        val result2 = runAmper(
            workingDir = tempDir,
            args = listOf("run"),
            bootstrapCacheDir = bootstrapCacheDir,
            // We want to test the check of JRE presence in the bootstrap dir, so we have to unset this
            amperJavaHomeMode = AmperJavaHomeMode.ForceUnset,
        )
        assertTrue("Process output must contain 'Hello for Shell Scripts Test' the second time. Output:\n${result2.stdout}") {
            result2.stdout.contains("Hello for Shell Scripts Test")
        }
        val nDownloadingLines2 = result2.stdout.lines().count { it.startsWith("Downloading ") }
        assertEquals(
            0,
            nDownloadingLines2,
            "Process output must not have 'Downloading ' lines the second time, got $nDownloadingLines2. Output:\n${result2.stdout}"
        )

        assertEquals(
            expected = requestedFiles,
            actual = listOf(LocalAmperPublication.distTgz),
            message = "The Amper script run should request the Amper distribution (and only this)",
        )
    }

    /**
     * It's expected on the start that wrappers and cli dist are published to maven local
     */
    @Test
    fun `first runs should be able to work concurrently`() = runBlocking {
        val templatePath = shellScriptExampleProject
        assertTrue { templatePath.isDirectory() }
        templatePath.copyToRecursively(tempDir, followLinks = false, overwrite = false)

        val bootstrapCacheDir = tempDir.resolve("boot strap")

        withContext(Dispatchers.IO) {
            repeat(15) {
                launch {
                    runAmper(
                        workingDir = tempDir,
                        args = listOf("--version"),
                        bootstrapCacheDir = bootstrapCacheDir,
                        // We want to test concurrent downloads of the JRE to the bootstrap dir, so we have to unset this
                        amperJavaHomeMode = AmperJavaHomeMode.ForceUnset,
                    )
                }
            }
        }
    }

    @Test
    fun `custom bootstrap cache`() = runBlocking {
        val templatePath = shellScriptExampleProject
        assertTrue { templatePath.isDirectory() }

        templatePath.copyToRecursively(tempDir, followLinks = false, overwrite = false)

        val bootstrapCacheDir = tempDir.resolve("my bootstrap cache")
        assertTrue("Bootstrap cache dir should start empty") {
            bootstrapCacheDir.notExists() || bootstrapCacheDir.listDirectoryEntries().isEmpty()
        }

        val result = runAmper(
            workingDir = tempDir,
            args = listOf("--version"),
            bootstrapCacheDir = bootstrapCacheDir,
            // We want to test the proper download of the JRE to the bootstrap dir, so we have to unset this
            amperJavaHomeMode = AmperJavaHomeMode.ForceUnset,
        )
        val nDownloadingLines = result.stdout.lines().count { it.startsWith("Downloading ") }
        assertEquals(
            2,
            nDownloadingLines,
            "Process output must have 'Downloading ' line twice, got $nDownloadingLines. Output:\n${result.stdout}"
        )
        assertTrue("Bootstrap cache dir should now exist") {
            bootstrapCacheDir.exists()
        }
        assertTrue("Bootstrap cache dir should now have the CLI distribution, but got:\n" +
                bootstrapCacheDir.listDirectoryEntries().joinToString("\n")) {
            bootstrapCacheDir.listDirectoryEntries("amper-cli-*").isNotEmpty()
        }
        assertTrue("Bootstrap cache dir should now have the JBR, but got:\n" +
                bootstrapCacheDir.listDirectoryEntries().joinToString("\n")) {
            bootstrapCacheDir.listDirectoryEntries("jbr-*").isNotEmpty()
        }
    }

    @Test
    fun `init command writes the same wrappers as published`() = runBlocking {
        val projectPath = shellScriptExampleProject
        assertTrue { projectPath.isDirectory() }

        val tempProjectRoot = tempDir.resolve("p p").resolve(projectPath.name)
        tempProjectRoot.createDirectories()

        // `amper init` should overwrite wrapper files
        tempProjectRoot.resolve("amper").writeText("w1")
        tempProjectRoot.resolve("amper.bat").writeText("w2")

        runAmper(
            workingDir = tempProjectRoot,
            args = listOf("init", "multiplatform-cli"),
            bootstrapCacheDir = tempDir.resolve("boot strap"),
            customAmperScriptPath = cliScript,
        )

        val windowsWrapper = tempProjectRoot.resolve("amper.bat")
        val unixWrapper = tempProjectRoot.resolve("amper")

        assertTrue(windowsWrapper.readText().count { it == '\r' } > 10,
            "Windows wrapper must have \\r in line separators: $windowsWrapper")
        assertTrue(unixWrapper.readText().count { it == '\r' } == 0,
            "Unix wrapper must not have \\r in line separators: $unixWrapper")

        if (OsFamily.current.isUnix) {
            assertTrue("Unix wrapper must be executable: $unixWrapper") { unixWrapper.isExecutable() }
        }

        for (wrapperName in listOf("amper", "amper.bat")) {
            val originalFile = tempDir.resolve(wrapperName)
            val actualFile = tempProjectRoot.resolve(wrapperName)

            if (!originalFile.readBytes().contentEquals(actualFile.readBytes())) {
                AssertionFailureBuilder.assertionFailure()
                    .message("Comparison failed:\n${generateUnifiedDiff(originalFile, actualFile)}")
                    .expected(FileInfo(originalFile.absolutePathString(), originalFile.readBytes()))
                    .actual(FileInfo(actualFile.absolutePathString(), actualFile.readBytes()))
                    .buildAndThrow()
            }
        }
    }

    @Test
    fun `init command should stop before overwriting files from template`() = runBlocking {
        val projectPath = shellScriptExampleProject
        assertTrue { projectPath.isDirectory() }

        val tempProjectRoot = tempDir.resolve(projectPath.name)
        tempProjectRoot.createDirectories()

        tempProjectRoot.resolve("project.yaml").writeText("w1")
        tempProjectRoot.resolve("jvm-cli").createDirectories()
        tempProjectRoot.resolve("jvm-cli/module.yaml").writeText("w2")

        val result = runAmper(
            workingDir = tempProjectRoot,
            args = listOf("init", "multiplatform-cli"),
            expectedExitCode = 1,
            bootstrapCacheDir = tempDir.resolve("boot strap"),
            assertEmptyStdErr = false,
            customAmperScriptPath = cliScript,
        )
        val expectedStderr = """
            ERROR: The following files already exist in the output directory and would be overwritten by the generation:
              jvm-cli/module.yaml
              project.yaml
            
            Please move, rename, or delete them before running the command again.
        """.trimIndent()
        assertEquals(expectedStderr, result.stderr.trim())
    }

    @Test
    fun `custom java home`() = runBlocking {
        val fakeUserCacheRoot = AmperUserCacheRoot(Dirs.userCacheRoot)
        val jdkHome = JdkDownloader.getJdk(fakeUserCacheRoot).homeDir

        val expectedAmperVersion = cliScript
            .readLines()
            .first { it.startsWith("set amper_version=") || it.startsWith("amper_version=") }
            .substringAfterLast('=')

        val result = runAmper(
            workingDir = tempDir,
            args = listOf("--version"),
            amperJavaHomeMode = AmperJavaHomeMode.Custom(jreHomePath = jdkHome),
            bootstrapCacheDir = tempDir.resolve("boot strap"),
        )
        val expectedVersionStringOld = "amper version $expectedAmperVersion"
        val expectedVersionString = Regex(
            Regex.escape("JetBrains Amper version $expectedAmperVersion+") +
                    "[A-Fa-f0-9]+\\+[A-Fa-f0-9]+")

        assertTrue("Process output must contain '${expectedVersionString.pattern}' or '$expectedVersionStringOld'. Output:\n${result.stdout}") {
            result.stdout.lines().any { it == expectedVersionStringOld || expectedVersionString.matches(it) }
        }

        val nDownloadingLines = result.stdout.lines().count { it.startsWith("Downloading ") }
        assertEquals(
            1,
            nDownloadingLines,
            "Process output must have 'Downloading ' line only once (for Amper itself), got $nDownloadingLines. Output:\n${result.stdout}"
        )

        // TODO Somehow assert that exactly this JRE is used by amper bootstrap
    }

    @Test
    fun `fails on wrong amper distribution checksum`() = runBlocking {
        assertWrongChecksum(Regex("\\b(amper_sha256=)[0-9a-fA-F]+"))
    }

    @Test
    fun `fails on wrong jre distribution checksum`() = runBlocking {
        assertWrongChecksum(Regex("\\b(jbr_sha512=)[0-9a-fA-F]+"))
    }

    private suspend fun assertWrongChecksum(checksumRegex: Regex) {
        val brokenScriptName = if (OsFamily.current.isWindows) "script.bat" else "script"
        val brokenScript = tempDir.resolve(brokenScriptName)

        val scriptContentWrongSha = cliScript.readText()
            .replace(checksumRegex, "\$1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        brokenScript.writeText(scriptContentWrongSha)
        brokenScript.toFile().setExecutable(true)
        assertTrue(brokenScript.isExecutable())

        val bootstrapCacheDir = tempDir.resolve("boot strap")
        assertTrue("Bootstrap cache dir should start empty") {
            bootstrapCacheDir.notExists() || bootstrapCacheDir.listDirectoryEntries().isEmpty()
        }
        val result = runAmper(
            workingDir = tempDir,
            args = listOf("--version"),
            expectedExitCode = 1,
            assertEmptyStdErr = false,
            bootstrapCacheDir = bootstrapCacheDir,
            customAmperScriptPath = brokenScript,
            // We want to test the failure to download the JRE to the bootstrap dir, we have to unset this
            amperJavaHomeMode = AmperJavaHomeMode.ForceUnset,
        )
        val expectedErrorRegex =
            Regex("""ERROR: Checksum mismatch for .+ \(downloaded from .+\): expected checksum aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa but got \w+""")
        assertTrue("Process stderr must match '$expectedErrorRegex'. Actual stderr:\n${result.stderr}") {
            // cmd.exe breaks lines unpredictably when calling powershell (it depends on its own buffer)
            result.stderr.replace("\r", "").replace("\n", "").matches(expectedErrorRegex)
        }
    }

    private val cliScript: Path
        get() = tempDir.resolve(scriptNameForCurrentOs)
}
