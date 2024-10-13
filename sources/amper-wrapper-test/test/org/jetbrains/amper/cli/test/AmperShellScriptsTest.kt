/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.awaitAndGetAllOutput
import org.jetbrains.amper.test.LocalAmperCli
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.m2repository
import org.jetbrains.amper.test.generateUnifiedDiff
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.opentest4j.FileInfo
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmperShellScriptsTest {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()
    private val tempDir: Path
        get() = tempDirExtension.path

    @RegisterExtension
    private val httpServer = HttpServerExtension(m2repository)

    private val shellScriptExampleProject = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects/shell-scripts")

    @BeforeEach
    fun prepareScript() {
        LocalAmperCli.checkPublicationIntegrity()
        LocalAmperCli.wrapperBat.copyTo(tempDir.resolve("amper.bat"))

        val targetSh = tempDir.resolve("amper")
        LocalAmperCli.wrapperSh.copyTo(targetSh)
        targetSh.toFile().setExecutable(true)
    }

    /**
     * It's expected on the start that wrappers and cli dist are published to maven local
     */
    @Test
    fun `shell script does not download or extract on subsequent run`() {
        val templatePath = shellScriptExampleProject
        assertTrue { templatePath.isDirectory() }

        templatePath.copyToRecursively(tempDir, followLinks = false, overwrite = false)

        val bootstrapCacheDir = tempDir.resolve("boot strap")

        runBuild(
            workingDir = tempDir,
            bootstrapCacheDir = bootstrapCacheDir,
            args = listOf("task", ":${tempDir.name}:runJvm"),
        ) { result ->
            val output = result.stdout

            assertTrue("Process output must contain 'Hello for Shell Scripts Test'. Output:\n$output") {
                output.contains("Hello for Shell Scripts Test")
            }

            assertTrue("Process output must have 'will now be provisioned' line twice. Output:\n$output") {
                output.lines().count { it.contains("will now be provisioned") } == 2
            }

            assertTrue("Process output must have 'Downloading ' line twice. Output:\n$output") {
                output.lines().count { it.startsWith("Downloading ") } == 2
            }

            assertTrue("Process output must have 'Extracting to $bootstrapCacheDir' line twice. Output:\n$output") {
                output.lines().count { it.startsWith("Extracting to $bootstrapCacheDir") } == 2
            }
        }

        runBuild(
            workingDir = tempDir,
            bootstrapCacheDir = bootstrapCacheDir,
            args = listOf("task", ":${tempDir.name}:runJvm"),
        ) { result ->
            val output = result.stdout

            assertTrue("Process output must contain 'Hello for Shell Scripts Test'. Output:\n$output") {
                output.contains("Hello for Shell Scripts Test")
            }

            assertTrue("Process output must not have 'will now be provisioned' lines. Output:\n$output") {
                output.lines().none { it.contains("will now be provisioned") }
            }

            assertTrue("Process output must not have 'Downloading ' lines. Output:\n$output") {
                output.lines().none { it.startsWith("Downloading ") }
            }

            assertTrue("Process output must not have 'Extracting ' lines. Output:\n$output") {
                output.lines().none { it.startsWith("Extracting ") }
            }
        }

        val cliDistZip = LocalAmperCli.distZip
        check(httpServer.requestedFiles.single() == cliDistZip) {
            "Only one file should be requested '$cliDistZip'. Files requested: ${httpServer.requestedFiles}"
        }
    }

    @Test
    fun `custom boostrap cache`() {
        val templatePath = shellScriptExampleProject
        assertTrue { templatePath.isDirectory() }

        templatePath.copyToRecursively(tempDir, followLinks = false, overwrite = false)

        val bootstrapCacheDir = tempDir.resolve("my bootstrap cache")
        assertTrue("Bootstrap cache dir should start empty") {
            bootstrapCacheDir.notExists() || bootstrapCacheDir.listDirectoryEntries().isEmpty()
        }

        runAmperVersion(bootstrapCacheDir = bootstrapCacheDir) { output ->
            assertTrue("Process output must have 'will now be provisioned' line twice. Output:\n$output") {
                output.lines().count { it.contains("will now be provisioned") } == 2
            }

            assertTrue("Process output must have 'Downloading ' line twice. Output:\n$output") {
                output.lines().count { it.startsWith("Downloading ") } == 2
            }

            assertTrue("Process output must have 'Extracting to $bootstrapCacheDir' line twice. Output:\n$output") {
                output.lines().count { it.startsWith("Extracting to $bootstrapCacheDir") } == 2
            }
        }
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
    fun `init command writes the same wrappers as published`() {
        val projectPath = shellScriptExampleProject
        assertTrue { projectPath.isDirectory() }

        val tempProjectRoot = tempDir.resolve("p p").resolve(projectPath.name)
        tempProjectRoot.createDirectories()

        // `amper init` should overwrite wrapper files
        tempProjectRoot.resolve("amper").writeText("w1")
        tempProjectRoot.resolve("amper.bat").writeText("w2")

        val bootstrapCacheDir = tempDir.resolve("boot strap")

        runBuild(workingDir = tempProjectRoot, bootstrapCacheDir = bootstrapCacheDir, args = listOf("init", "multiplatform-cli")) {
        }

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
    fun `init command should stop before overwriting files from template`() {
        val projectPath = shellScriptExampleProject
        assertTrue { projectPath.isDirectory() }

        val tempProjectRoot = tempDir.resolve(projectPath.name)
        tempProjectRoot.createDirectories()

        tempProjectRoot.resolve("project.yaml").writeText("w1")
        tempProjectRoot.resolve("jvm-cli").createDirectories()
        tempProjectRoot.resolve("jvm-cli/module.yaml").writeText("w2")

        val bootstrapCacheDir = tempDir.resolve("boot strap")

        runBuild(
            workingDir = tempProjectRoot,
            bootstrapCacheDir = bootstrapCacheDir,
            args = listOf("init", "multiplatform-cli"),
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        ) { result ->
            assertEquals("""
                ERROR: Files already exist in the project root:
                  jvm-cli/module.yaml
                  project.yaml
            """.trimIndent(),
                result.stderr
                    .replace("\r", "")
                    .lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            )
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
                        "[A-Fa-f0-9]+\\+[A-Fa-f0-9]+")

            assertTrue("Process output must contain '${expectedVersionString.pattern}' or '$expectedVersionStringOld'. Output:\n$output") {
                output.lines().any { it == expectedVersionStringOld || expectedVersionString.matches(it) }
            }

            assertTrue("Process output must have 'will now be provisioned' line only once (for Amper itself). Output:\n$output") {
                output.lines().count { it.contains("will now be provisioned") } == 1
            }

            assertTrue("Process output must have 'Downloading ' line only once (for Amper itself). Output:\n$output") {
                output.lines().count { it.startsWith("Downloading ") } == 1
            }

            // TODO Somehow assert that exactly this JRE is used by amper bootstrap
        }
    }

    @Test
    fun `fails on wrong amper distribution checksum`() {
        assertWrongChecksum(Regex("\\b(amper_sha256=)[0-9a-fA-F]+"))
    }

    @Test
    fun `fails on wrong jre distribution checksum`() {
        assertWrongChecksum(Regex("\\b(jbr_sha512=)[0-9a-fA-F]+"))
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

    private fun runBuild(
        workingDir: Path,
        bootstrapCacheDir: Path = tempDir.resolve("boot strap"),
        args: List<String>,
        expectedExitCode: Int = 0,
        assertEmptyStdErr: Boolean = true,
        outputAssertions: (ProcessResult) -> Unit,
    ) {
        val process = ProcessBuilder()
            .directory(workingDir.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .also {
                it.environment()["AMPER_BOOTSTRAP_CACHE_DIR"] = bootstrapCacheDir.pathString
                it.environment()["AMPER_JRE_DOWNLOAD_ROOT"] = httpServer.cacheRootUrl
                it.environment()["AMPER_DOWNLOAD_ROOT"] = httpServer.wwwRootUrl
            }
            .command(cliScript.pathString, *args.toTypedArray())
            .start()
        process.outputStream.close()

        val result = runBlocking {
            process.awaitAndGetAllOutput(object : ProcessOutputListener {
                override fun onStdoutLine(line: String) {
                    println("OUT> $line")
                }

                override fun onStderrLine(line: String) {
                    println("ERR> $line")
                }
            })
        }

        assertEquals(
            expectedExitCode, result.exitCode,
            "Exit code must be $expectedExitCode, but got ${result.exitCode}." +
                    " Process stderr:\n${result.stderr}")
        if (assertEmptyStdErr) {
            assertTrue(result.stderr.isBlank(), "Process stderr must be empty, but got:\n${result.stderr}")
        }
        outputAssertions(result)
    }

    private fun runAmperVersion(
        customJavaHome: Path? = null,
        customScript: Path? = null,
        expectedExitCode: Int = 0,
        bootstrapCacheDir: Path = tempDir.resolve("boot strap"),
        outputAssertions: (String) -> Unit,
    ) {

        val process = ProcessBuilder()
            .directory(tempDir.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .also {
                it.environment()["AMPER_BOOTSTRAP_CACHE_DIR"] = bootstrapCacheDir.pathString
                if (customJavaHome != null) {
                    it.environment()["AMPER_JAVA_HOME"] = customJavaHome.pathString
                }

                it.environment()["AMPER_DOWNLOAD_ROOT"] = httpServer.wwwRootUrl
                it.environment()["AMPER_JRE_DOWNLOAD_ROOT"] = httpServer.cacheRootUrl
            }
            .command((customScript ?: cliScript).pathString, "--version")
            .start()

        process.outputStream.close()
        val processOutput = process.inputStream.readAllBytes().decodeToString()
        val rc = process.waitFor()

        assertEquals(expectedExitCode, rc, "Exit code must be $expectedExitCode, but got $rc. Process output:\n$processOutput")

        outputAssertions(processOutput)
    }

    private val cliScriptExtension = if (OsFamily.current.isWindows) ".bat" else ""

    private val cliScript: Path by lazy {
        val script = tempDir.resolve("amper$cliScriptExtension")
        assertTrue("Script must exist: $script") { script.exists() }
        assertTrue("Script must be executable: $script") { script.isExecutable() }
        script
    }
}
