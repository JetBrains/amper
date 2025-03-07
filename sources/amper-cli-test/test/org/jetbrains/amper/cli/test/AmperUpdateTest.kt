/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.test.LocalAmperPublication
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperUpdateTest : AmperCliTestBase() {

    private fun newTestProjectDir(): Path = tempRoot.resolve("new").also { it.createDirectories() }

    @Test
    fun `update command without options creates wrappers with confirmation`() = runSlowTest {
        val projectDir = newTestProjectDir()

        val result = runCli(projectDir, "update", stdin = ProcessInput.Text("y\n"))

        assertTrue(result.stdout.contains("Would you like to create"), "amper should ask for confirmation")
        assertEquals(listOf("amper", "amper.bat"), projectDir.relativeChildren(), "amper scripts should be created")
    }

    @Test
    fun `update --create command creates wrappers without confirmation`() = runSlowTest {
        val projectDir = newTestProjectDir()

        val result = runCli(projectDir, "update", "--create")

        assertFalse(result.stdout.contains("?"), "amper should not ask for confirmation")
        assertEquals(listOf("amper", "amper.bat"), projectDir.relativeChildren(), "amper scripts should be created")
    }

    @Test
    fun `update command without options replaces existing wrappers with latest release`() = runSlowTest {
        val projectDir = newTestProjectDir()
        LocalAmperPublication.setupWrappersIn(projectDir)

        val (bashVersion, batVersion, result) = runAmperUpdate(projectDir)

        assertTrue(result.stdout.contains("Update successful"), "Update should be successful")
        assertNotEquals("1.0-SNAPSHOT", bashVersion, "amper bash script should have the new version")
        assertNotEquals("1.0-SNAPSHOT", batVersion, "amper bat script should have the new version")
        assertFalse(bashVersion.contains("-dev-"), "amper bash script should not get a dev version, got $bashVersion")
        assertFalse(batVersion.contains("-dev-"), "amper bat script should not get a dev version, got $batVersion")
    }

    @Test
    fun `update --dev command replaces existing wrappers with latest dev version`() = runSlowTest {
        val projectDir = newTestProjectDir()
        LocalAmperPublication.setupWrappersIn(projectDir)

        val (bashVersion, batVersion, result) = runAmperUpdate(projectDir, "--dev")

        assertTrue(result.stdout.contains("Update successful"), "Update should be successful")
        // This is not technically correct: right after a release, the release version should be picked up
        // (it's the latest among all versions, release + dev)
        assertTrue(bashVersion.contains("-dev-"), "amper bash script new version should contain '-dev-', got $bashVersion")
        assertTrue(batVersion.contains("-dev-"), "amper bat script new version should contain '-dev-', got $batVersion")
    }

    @Test
    fun `update --target-version command replaces existing wrappers with specific version`() = runSlowTest {
        val projectDir = newTestProjectDir()
        LocalAmperPublication.setupWrappersIn(projectDir)

        val (bashVersion, batVersion, result) = runAmperUpdate(projectDir, "--target-version=0.6.0-dev-2229")

        assertTrue(result.stdout.contains("Update successful"), "Update should be successful")
        assertEquals("0.6.0-dev-2229", bashVersion, "amper bash script should have the new version")
        assertEquals("0.6.0-dev-2229", batVersion, "amper bat script should have the new version")
    }

    @Test
    fun `can downgrade from current to 0_5_0`() = runSlowTest {
        val projectDir = newTestProjectDir()
        LocalAmperPublication.setupWrappersIn(projectDir)

        val (bashVersion, batVersion, result) = runAmperUpdate(projectDir, "--target-version=0.5.0")

        assertTrue(result.stdout.contains("Update successful"), "Update should be successful")
        assertEquals("0.5.0", bashVersion, "amper bash script should have the new version")
        assertEquals("0.5.0", batVersion, "amper bat script should have the new version")
    }

    @Test
    fun `can update from 0_5_0 to current`() = runSlowTest {
        val projectDir = newTestProjectDir()
        runCli(projectDir, "update", "--target-version=0.5.0", "--create")

        assertCanUpdateToCurrent(projectDir)
    }

    @Disabled("The current dev versions still have the update bug. Re-enable when AMPER-4164 is fixed")
    @Test
    fun `can update from latest dev to current`() = runSlowTest {
        val projectDir = newTestProjectDir()
        runCli(projectDir, "update", "--dev", "--create")

        assertCanUpdateToCurrent(projectDir)
    }

    private suspend fun assertCanUpdateToCurrent(projectDir: Path) {
        val (bashVersion, batVersion) = runAmperUpdate(
            projectDir,
            "--target-version=1.0-SNAPSHOT",
            "--repository=$localAmperDistRepoUrl",
        )
        assertEquals("1.0-SNAPSHOT", bashVersion, "amper bash script should have the new version")
        assertEquals("1.0-SNAPSHOT", batVersion, "amper bat script should have the new version")
    }

    private data class UpdateResult(
        val bashVersion: String,
        val batVersion: String,
        val commandResult: AmperCliResult,
    )

    private suspend fun runAmperUpdate(projectDir: Path, vararg options: String): UpdateResult {
        val result = runCli(
            projectRoot = projectDir,
            "update", *options,
            customAmperScriptPath = projectDir.resolve(scriptNameForCurrentOs),
        )
        assertEquals(listOf("amper", "amper.bat"), projectDir.relativeChildren(), "amper scripts should still be there")

        // On Windows, the bat script sometimes cannot be changed in-place, so we have to wait for the late replacement
        if (DefaultSystemInfo.detect().family.isWindows) {
            awaitWrapperVersionsMatchIn(projectDir)
        }
        return UpdateResult(
            bashVersion = projectDir.readVersionInBashScript(),
            batVersion = projectDir.readVersionInBatchScript(),
            commandResult = result,
        )
    }

    private suspend fun awaitWrapperVersionsMatchIn(projectDir: Path) {
        // we switch context to use real time instead of the virtual time from runTest (test coroutine scheduler)
        withContext(Dispatchers.IO) {
            repeat(20) {
                if (projectDir.readVersionInBashScript() == projectDir.readVersionInBatchScript()) {
                    return@withContext
                }
                delay(100.milliseconds)
            }
            fail(
                "Batch script version doesn't match bash script version in $projectDir after 20 attempts.\n" +
                        "Version in 'amper':     ${projectDir.readVersionInBashScript()}\n" +
                        "Version in 'amper.bat': ${projectDir.readVersionInBatchScript()}"
            )
        }
    }

    private fun Path.readVersionInBashScript(): String = this.resolve("amper")
        .readLines()
        .first { it.startsWith("amper_version=") }
        .removePrefix("amper_version=")

    private fun Path.readVersionInBatchScript(): String = this.resolve("amper.bat")
        .readLines()
        .first { it.startsWith("set amper_version=") }
        .removePrefix("set amper_version=")

    private fun Path.relativeChildren(): List<String> =
        listDirectoryEntries().map { it.relativeTo(this).pathString }.sorted()
}
